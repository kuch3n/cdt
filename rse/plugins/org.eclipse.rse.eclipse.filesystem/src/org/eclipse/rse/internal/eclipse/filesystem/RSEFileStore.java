/********************************************************************************
 * Copyright (c) 2006, 2007 IBM Corporation. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is 
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Initial Contributors:
 * The following IBM employees contributed to the Remote System Explorer
 * component that contains this file: David McKnight, Kushal Munir, 
 * Michael Berger, David Dykstal, Phil Coulthard, Don Yantzi, Eric Simpson, 
 * Emily Bruner, Mazen Faraj, Adrian Storisteanu, Li Ding, and Kent Hawley.
 * 
 * Contributors:
 * Kushal Munir (IBM) - moved to internal package
 ********************************************************************************/

package org.eclipse.rse.internal.eclipse.filesystem;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.internal.filesystem.Policy;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.rse.core.subsystems.RemoteChildrenContentsType;
import org.eclipse.rse.services.clientserver.messages.SystemMessageException;
import org.eclipse.rse.services.files.IHostFile;
import org.eclipse.rse.subsystems.files.core.model.RemoteFileFilterString;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.FileServiceSubSystem;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFile;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFileSubSystem;
import org.eclipse.rse.subsystems.files.core.subsystems.IRemoteFileSubSystemConfiguration;
import org.eclipse.rse.subsystems.files.core.subsystems.RemoteFileContext;


public class RSEFileStore extends FileStore implements IFileStore 
{
	private IRemoteFile _remoteFile;
	private IRemoteFileSubSystem _subSystem;
	private IFileStore _parent;
	
	public RSEFileStore(IFileStore parent, IRemoteFile remoteFile) {
		_remoteFile = remoteFile;
		_parent = parent;
		_subSystem = _remoteFile.getParentRemoteFileSubSystem();
	}
	
	public IRemoteFileSubSystem getRemoteFileSubSystem() {
		return _subSystem;
	}
	
	public String[] childNames(int options, IProgressMonitor monitor) {
		
		String[] names;
		
		if (!_remoteFile.isStale() && _remoteFile.hasContents(RemoteChildrenContentsType.getInstance()) && !(_subSystem instanceof IFileServiceSubSystem))
		{
			Object[] children = _remoteFile.getContents(RemoteChildrenContentsType.getInstance());
			names = new String[children.length];
			                
			for (int i = 0; i < children.length; i++)
			{
				names[i] = ((IRemoteFile)children[i]).getName();
			}
		}
		else
		{
			try {
				
				IRemoteFile[] children = null;
				
				if (_subSystem instanceof FileServiceSubSystem) {
					FileServiceSubSystem fileServiceSubSystem = ((FileServiceSubSystem)_subSystem);
					IHostFile[] results = fileServiceSubSystem.getFileService().getFilesAndFolders(monitor, _remoteFile.getAbsolutePath(), "*"); //$NON-NLS-1$
					IRemoteFileSubSystemConfiguration config = _subSystem.getParentRemoteFileSubSystemConfiguration();
					RemoteFileFilterString filterString = new RemoteFileFilterString(config, _remoteFile.getAbsolutePath(), "*"); //$NON-NLS-1$
					filterString.setShowFiles(true);
					filterString.setShowSubDirs(true);
					RemoteFileContext context = new RemoteFileContext(_subSystem, _remoteFile, filterString);
					children = fileServiceSubSystem.getHostFileToRemoteFileAdapter().convertToRemoteFiles(fileServiceSubSystem, context, _remoteFile, results);
				}
				else {
					children = _subSystem.listFoldersAndFiles(_remoteFile, "*", monitor); //$NON-NLS-1$
				}
				
				names = new String[children.length];
				
				for (int i = 0; i < children.length; i++) {
					names[i] = children[i].getName();
				}		
			}
			catch (SystemMessageException e) {
				names = new String[0];
			}
		}
		
		return names;
	}
	
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) throws CoreException 
	{	
		if (_remoteFile.isStale())
		{
			try
			{
				_remoteFile = _subSystem.getRemoteFileObject(_remoteFile.getAbsolutePath());
			}
			catch (Exception e)
			{				
			}
		}
		
		FileInfo info = new FileInfo(getName());
		boolean exists = _remoteFile.exists();
		
		info.setExists(exists);

		if (exists)
		{
			info.setLastModified(_remoteFile.getLastModified());
			boolean isDir = _remoteFile.isDirectory();
			info.setDirectory(isDir);
			info.setAttribute(EFS.ATTRIBUTE_READ_ONLY, !_remoteFile.canWrite());
			info.setAttribute(EFS.ATTRIBUTE_EXECUTABLE, _remoteFile.isExecutable());
			info.setAttribute(EFS.ATTRIBUTE_ARCHIVE, _remoteFile.isArchive());
			info.setAttribute(EFS.ATTRIBUTE_HIDDEN, _remoteFile.isHidden());

			if (!isDir)
			{
				info.setLength(_remoteFile.getLength());
			}
		}
		
		info.setName(getName());
		return info;
	}
	
	public String getName() 
	{
		return _remoteFile.getName();
	}
	
	public IFileStore getParent() 
	{
		if (_parent == null)
		{
			_parent = new RSEFileStore(null, _remoteFile.getParentRemoteFile());
		}
		return _parent;
	}
	
	public boolean isParentOf(IFileStore other) 
	{
		if (other instanceof RSEFileStore)
		{
			RSEFileStore otherWrapper = (RSEFileStore)other;
			RSEFileStore parent = (RSEFileStore)otherWrapper.getParent();
			return _remoteFile == parent._remoteFile;
		}
		else
		{
			return false;
		}
	}
	
	public synchronized InputStream  openInputStream(int options, IProgressMonitor monitor) throws CoreException 
	{
		if (_remoteFile.exists())
		{
			if (_remoteFile.isFile() && _subSystem.isConnected() && _subSystem instanceof IFileServiceSubSystem)
			{
				IFileServiceSubSystem fileSubSystem = (IFileServiceSubSystem)_subSystem;
				
				try {
					return fileSubSystem.getFileService().getInputStream(monitor, _remoteFile.getParentPath(), _remoteFile.getName(), true);
				}
				catch (SystemMessageException e) {
					return null;
				}
			}
			
			if (_remoteFile.isDirectory()) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.getDefault().getBundle().getSymbolicName(), "This is a directory")); //$NON-NLS-1$
			}
		}
		return null;
	}
	
	public class RefreshJob extends Job
	{
		private IResource _resource;
		private int _depth;
		public RefreshJob(IResource resource, int depth)
		{
			super("Refresh"); //$NON-NLS-1$
			_resource = resource;			
			_depth = depth;
		}
		
		public IStatus run(IProgressMonitor monitor)				
		{
			try
			{
				_resource.refreshLocal(_depth, monitor);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			return Status.OK_STATUS;
		}
	}
	
	public URI toURI() 
	{
		try 
		{
			String path = _remoteFile.getAbsolutePath();
			if (path.charAt(0) != '/')
			{
				path = "/" + path.replace('\\', '/'); //$NON-NLS-1$
			}
			return new URI("rse", _subSystem.getHost().getHostName(), path, null); //$NON-NLS-1$
		} 
		catch (URISyntaxException e) 
		{
			throw new RuntimeException(e);
		}	
	}

	public IFileStore mkdir(int options, IProgressMonitor monitor) throws CoreException 
	{
		if (!_remoteFile.exists())
		{
		try
		{
			_subSystem.createFolder(_remoteFile);
			_remoteFile = _subSystem.getRemoteFileObject(_remoteFile.getAbsolutePath());
		}
		catch (Exception e)
		{			
		}
		}
		return this;
	}

	public OutputStream openOutputStream(int options, IProgressMonitor monitor) throws CoreException {
		monitor = Policy.monitorFor(monitor);
		
		try {
			if (!_remoteFile.exists())
			{
				_subSystem.createFile(_remoteFile);
				_remoteFile = _subSystem.getRemoteFileObject(_remoteFile.getAbsolutePath());
			}
		}
		catch (Exception e) {
			return null;
		}
			
		if (_remoteFile.isFile() && _subSystem instanceof FileServiceSubSystem) {
			IFileServiceSubSystem fileSubSystem = (IFileServiceSubSystem)_subSystem;
			
			try {
				return fileSubSystem.getFileService().getOutputStream(monitor, _remoteFile.getParentPath(), _remoteFile.getName(), true);
			}
			catch (SystemMessageException e) {
				return null;
			}
		}
		else {
			
			if (_remoteFile.isDirectory()) {
				throw new CoreException(new Status(IStatus.ERROR, Activator.getDefault().getBundle().getSymbolicName(), "This is a directory")); //$NON-NLS-1$
			}
			else {
				return null;
			}
		}
	}

	public IFileStore getChild(IPath path) 
	{
		IFileStore result = this;
		for (int i = 0, imax = path.segmentCount(); i < imax; i++)
		{
			String segment = path.segment(i);
			result = result.getChild(segment);
		}
		
		return result;
	}
	
	public IFileStore getChild(String name) 
	{
		if (!_remoteFile.isStale() && _remoteFile.hasContents(RemoteChildrenContentsType.getInstance(), name))
		{
			Object[] children = _remoteFile.getContents(RemoteChildrenContentsType.getInstance(), name);
			if (children != null && children.length > 0)
			{
				return FileStoreConversionUtility.convert(_parent, (IRemoteFile)children[0]);
			}
			else
			{
				// need empty one
				try
				{
					IRemoteFile child = _subSystem.getRemoteFileObject(_remoteFile, name);
					return FileStoreConversionUtility.convert(_parent, child);
				}
				catch (Exception e)
				{
					
				}
			}
		}
		else if (_remoteFile.isDirectory())
		{
			try
			{
				IRemoteFile child = _subSystem.getRemoteFileObject(_remoteFile, name);
				return FileStoreConversionUtility.convert(_parent, child);
			}
			catch (Exception e)
			{
				
			}

		}
		return null;
	}

	public File toLocalFile(int options, IProgressMonitor monitor) throws CoreException 
	{
		if (options == EFS.CACHE) {
			return super.toLocalFile(options, monitor);
		}
		else {
			if (_remoteFile.exists() && _subSystem instanceof IFileServiceSubSystem)
			{
				RSEFileCache cache = RSEFileCache.getInstance();
				IFileServiceSubSystem fileServiceSubSystem = (IFileServiceSubSystem)_subSystem;
				InputStream inputStream = null;
				
				try {
					
					if (_remoteFile.isFile()) {
						inputStream = fileServiceSubSystem.getFileService().getInputStream(monitor, _remoteFile.getParentRemoteFileSubSystem().getHost().getHostName(), _remoteFile.getName(), true);
					}
					
					return cache.writeToCache(_remoteFile, inputStream);
				}
				catch (SystemMessageException e) {
					return null;
				}
			}
			else {
				return null;
			}
		}
	}

	public void delete(int options, IProgressMonitor monitor) throws CoreException 
	{
		try {
			_subSystem.delete(_remoteFile, monitor);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}