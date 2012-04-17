/* FeatureIDE - An IDE to support feature-oriented software development
 * Copyright (C) 2005-2012  FeatureIDE team, University of Magdeburg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 *
 * See http://www.fosd.de/featureide/ for further information.
 */
package de.ovgu.featureide.ui.editors;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.texteditor.IDocumentProvider;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.fm.core.FMCorePlugin;
import de.ovgu.featureide.fm.core.Feature;
import de.ovgu.featureide.fm.core.FeatureModel;
import de.ovgu.featureide.fm.core.PropertyConstants;
import de.ovgu.featureide.fm.core.configuration.Configuration;
import de.ovgu.featureide.fm.core.configuration.ConfigurationReader;
import de.ovgu.featureide.fm.core.configuration.ConfigurationWriter;
import de.ovgu.featureide.fm.ui.FMUIPlugin;
import de.ovgu.featureide.fm.ui.editors.featuremodel.GUIDefaults;
import de.ovgu.featureide.ui.UIPlugin;
import de.ovgu.featureide.ui.editors.confuguration.AdvancedConfigurationPage;
import de.ovgu.featureide.ui.editors.confuguration.ConfigurationPage;
import de.ovgu.featureide.ui.editors.confuguration.IConfigurationEditorPage;
import de.ovgu.featureide.ui.editors.confuguration.TextEditorPage;

/**
 * Displays a configuration file.
 * 
 * @author Constanze Adler
 * @author Christian Becker
 * @author Jens Meinicke
 * @author Hannes Smurawsky
 */
/*
 * TODO should this be refactored into .fm.ui
 */
public class ConfigurationEditor extends MultiPageEditorPart implements
		GUIDefaults, PropertyConstants, PropertyChangeListener,
		IResourceChangeListener {

	public ConfigurationPage configurationPage;
	private boolean configurationPageUsed = true;
	
	public AdvancedConfigurationPage advancedConfigurationPage;
	private boolean advancedConfigurationPageUsed = false;

	private TextEditorPage sourceEditor;

	public IFile file;

	public FeatureModel featureModel;

	public Configuration configuration;

	private int oldPageIndex = -1;

	private boolean closeEditor;

	private boolean isPageModified = false;
	
	private LinkedList<IConfigurationEditorPage> extensionPages = new LinkedList<IConfigurationEditorPage>();
	
	/**
	 * @return the extensionPages
	 */
	public LinkedList<IConfigurationEditorPage> getExtensionPages() {
		return extensionPages;
	}
	
	private IPartListener iPartListener = new IPartListener() {

		@Override
		public void partBroughtToTop(IWorkbenchPart part) {
		}

		@Override
		public void partClosed(IWorkbenchPart part) {
			configurationPage.cancelColorJob();
			if (featureModel != null)
				featureModel.removeListener(ConfigurationEditor.this);
		}

		@Override
		public void partDeactivated(IWorkbenchPart part) {
		}

		@Override
		public void partOpened(IWorkbenchPart part) {
		}

		@Override
		public void partActivated(IWorkbenchPart part) {
		}
	};

	@Override
	protected void setInput(IEditorInput input) {
		file = (IFile) input.getAdapter(IFile.class);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		super.setInput(input);
		getSite().getPage().addPartListener(iPartListener);
		IFeatureProject featureProject = CorePlugin.getFeatureProject(file);
		if (featureProject == null) {
			IProject project = file.getProject();
			UIPlugin.getDefault().logWarning(
					"Project " + project.getName() + " is no featureproject.");
			return;
		}
		featureModel = featureProject.getFeatureModel();
		configuration = new Configuration(featureModel, true);
		try {
			new ConfigurationReader(configuration).readFromFile(file);
			isPageModified = isModified(file);
		} catch (Exception e) {
			FMCorePlugin.getDefault().logError(e);
		}
		setPartName(file.getName());
		featureModel.addListener(this);
		firePropertyChange(IEditorPart.PROP_DIRTY);
		getExtensions();
	}

	/**
	 * Gets all extensions for this extension point.
	 */
	private void getExtensions() {
		IConfigurationElement[] config = Platform.getExtensionRegistry()
			.getConfigurationElementsFor(
			UIPlugin.PLUGIN_ID + ".ConfigurationEditor");
		try {
			for (IConfigurationElement e : config) {
				final Object o = e.createExecutableExtension("class");
				if (o instanceof IConfigurationEditorPage) {
					extensionPages.add(((IConfigurationEditorPage) o));
				}
			}
		} catch (Exception e) {
			FMCorePlugin.getDefault().logError(e);
		}
	}
	
	/**
	 * @param configurationEditor
	 *            file
	 * @return true if configuration of the tree do not equal the configuration
	 *         of the file
	 */
	private boolean isModified(IFile iFile) {
		LinkedList<String> treeFeatures = new LinkedList<String>();
		for (Feature feature : configuration.getSelectedFeatures()) {
			if (feature.isLayer()) {
				treeFeatures.add(feature.getName());
			}
		}
		File file = iFile.getRawLocation().toFile();
		try {
			Scanner scanner = new Scanner(file);
			String line = null;
			while (scanner.hasNext()) {
				line = scanner.next();
				if (line.startsWith("#") || line.isEmpty()) {
					continue;
				}
				StringTokenizer tokenizer = new StringTokenizer(line);
				while (tokenizer.hasMoreTokens()) {
					String name = tokenizer.nextToken();
					if (treeFeatures.contains(name)) {
						treeFeatures.remove(name);
					} else {
						scanner.close();
						return true;
					}
				}
			}
			scanner.close();
			if (treeFeatures.size() != 0) {
				return true;
			}
		} catch (FileNotFoundException e) {
			UIPlugin.getDefault().logError(e);
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.
	 * PropertyChangeEvent)
	 */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (isDirty())
			setConfiguration();
		else
			setConfigurationFromFile();
		// Reinitialize the actual pages
		if (oldPageIndex == advancedConfigurationPage.getIndex()) {
			advancedConfigurationPage.propertyChange(null);
			configurationPageUsed = false;
		} else if (oldPageIndex == configurationPage.getIndex()) {
			configurationPage.propertyChange(null);
			advancedConfigurationPageUsed = false;
		} else {
			advancedConfigurationPageUsed = false;
			configurationPageUsed = false;
		}
		for (IConfigurationEditorPage page : extensionPages) {
			if (oldPageIndex == page.getIndex()) {
				page.propertyChange(evt);
			}
		}
		if (oldPageIndex == sourceEditor.getIndex()) {
			UIJob job = new UIJob("refresh source page") {
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					sourceEditor.propertyChange(null);
					return Status.OK_STATUS;
				}
			};
			job.setPriority(Job.SHORT);
			job.schedule();
		}
	}

	@Override
	public boolean isDirty() {
		return isPageModified || super.isDirty();
	}

	private void setConfigurationFromFile() {
		IFeatureProject featureProject = CorePlugin.getFeatureProject(file);
		featureModel = featureProject.getFeatureModel();
		configuration = new Configuration(featureModel, true);
		try {
			new ConfigurationReader(configuration).readFromFile(file);
		} catch (Exception e) {
			FMCorePlugin.getDefault().logError(e);
		}
	}

	private void setConfiguration() {
		IFeatureProject featureProject = CorePlugin.getFeatureProject(file);
		featureModel = featureProject.getFeatureModel();
		String text = new ConfigurationWriter(configuration)
				.writeIntoString(file);
		configuration = new Configuration(featureModel, true);
		try {
			new ConfigurationReader(configuration).readFromString(text);
		} catch (Exception e) {
			FMCorePlugin.getDefault().logError(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.part.MultiPageEditorPart#createPages()
	 */
	@Override
	protected void createPages() {
		createConfigurationPage();
		createAdvancedConfigurationPage();
		createExtensionPages();
		createSourcePage();
	}

	private void createConfigurationPage() {
		configurationPage = new ConfigurationPage();
		configurationPage.setConfigurationEditor(this);
		try {
			configurationPage.setIndex(addPage(configurationPage,
					getEditorInput()));
			setPageText(configurationPage.getIndex(), configurationPage.getPageText());
		} catch (PartInitException e) {
			UIPlugin.getDefault().logError(e);
		}
		configurationPage.propertyChange(null);
	}

	private void createAdvancedConfigurationPage() {
		advancedConfigurationPage = new AdvancedConfigurationPage();
		advancedConfigurationPage.setConfigurationEditor(this);
		try {
			advancedConfigurationPage.setIndex(addPage(advancedConfigurationPage,
					getEditorInput()));
			setPageText(advancedConfigurationPage.getIndex(),
					advancedConfigurationPage.getPageText());
		} catch (PartInitException e) {
			UIPlugin.getDefault().logError(e);
		}
	}

	private void createExtensionPages() {
		for (IConfigurationEditorPage page : extensionPages) {
			try {
				page.setConfigurationEditor(this);
				page = page.getPage();
				page.setIndex(addPage(page, getEditorInput()));
				setPageText(page.getIndex(), page.getPageText());
			} catch (PartInitException e) {
				UIPlugin.getDefault().logError(e);
			}
			page.propertyChange(null);
		}
	}

	private void createSourcePage() {
		sourceEditor = new TextEditorPage();
		sourceEditor.setConfigurationEditor(this);
		try {
			sourceEditor.setIndex(addPage(sourceEditor, getEditorInput()));
			setPageText(sourceEditor.getIndex(), sourceEditor.getPageText());
		} catch (PartInitException e) {
			FMUIPlugin.getDefault().logError(e);
		}
	}

	@Override
	protected void pageChange(int newPageIndex) {
		if (oldPageIndex == sourceEditor.getIndex()) {
			IDocumentProvider provider = sourceEditor.getDocumentProvider();
			IDocument document = provider.getDocument(sourceEditor
					.getEditorInput());
			if (!new ConfigurationWriter(configuration).writeIntoString(file)
					.equals(document.get())) {
				configuration = new Configuration(featureModel, true);
				try {
					new ConfigurationReader(configuration)
							.readFromString(document.get());
				} catch (Exception e) {
					FMCorePlugin.getDefault().logError(e);
				}
				advancedConfigurationPage.propertyChange(null);
			}
		} else if (oldPageIndex == configurationPage.getIndex()) {
			configurationPage.resetColor();
		} else {
			for (IConfigurationEditorPage page : extensionPages) {
				if (page.getIndex() == oldPageIndex) {
					page.pageChangeFrom(newPageIndex);
					break;
				}
			}
		}

		if (oldPageIndex != -1) {
			if (newPageIndex == configurationPage.getIndex()){
				if (configurationPageUsed)
					configurationPage.updateTree();
				else {
					configurationPage.propertyChange(null);
					configurationPageUsed = true;
				}
			} else if (newPageIndex == advancedConfigurationPage.getIndex()) {
				if (advancedConfigurationPageUsed)
					advancedConfigurationPage.updateTree();
				else {
					advancedConfigurationPage.propertyChange(null);
					advancedConfigurationPageUsed = true;
				}
			} else if (newPageIndex == sourceEditor.getIndex()) {
				sourceEditor.propertyChange(null);
			} else {
				for (IConfigurationEditorPage page : extensionPages) {
					if (page.getIndex() == newPageIndex) {
						page.pageChangeTo(oldPageIndex);
						break;
					}
				}
			}
		}
		oldPageIndex = newPageIndex;
		super.pageChange(newPageIndex);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.part.EditorPart#doSave(org.eclipse.core.runtime.
	 * IProgressMonitor)
	 */
	@Override
	public void doSave(IProgressMonitor monitor) {
		try {
			new ConfigurationWriter(configuration).saveToFile(file);
			firePropertyChange(IEditorPart.PROP_DIRTY);
			isPageModified = false;
		} catch (CoreException e) {
			UIPlugin.getDefault().logError(e);
		}
		advancedConfigurationPage.doSave(monitor);
		configurationPage.doSave(monitor);
		for (IConfigurationEditorPage page : extensionPages) {
			page.doSave(monitor);
		}
		sourceEditor.doSave(monitor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.part.EditorPart#doSaveAs()
	 */
	@Override
	public void doSaveAs() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.part.EditorPart#isSaveAsAllowed()
	 */
	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org
	 * .eclipse.core.resources.IResourceChangeEvent)
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		if (event.getResource() == null)
			return;
		if (event.getResource().getType() == IResource.PROJECT)
			closeEditor = true;
		final IEditorInput input = getEditorInput();
		if (!(input instanceof IFileEditorInput))
			return;
		final IFile jmolfile = ((IFileEditorInput) input).getFile();

		/*
		 * Closes editor if resource is deleted
		 */
		if ((event.getType() == IResourceChangeEvent.POST_CHANGE)
				&& closeEditor) {
			IResourceDelta rootDelta = event.getDelta();
			// get the delta, if any, for the documentation directory
			final List<IResource> deletedlist = new ArrayList<IResource>();
			IResourceDelta docDelta = rootDelta.findMember(jmolfile
					.getFullPath());
			if (docDelta != null) {
				IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {
					public boolean visit(IResourceDelta delta) {
						// only interested in removal changes
						if (((delta.getFlags() & IResourceDelta.REMOVED) == 0)
								&& closeEditor) {
							deletedlist.add(delta.getResource());
						}
						return true;
					}
				};
				try {
					docDelta.accept(visitor);
				} catch (CoreException e) {
					FMUIPlugin.getDefault().logError(e);
				}
			}
			if (deletedlist.size() > 0 && deletedlist.contains(jmolfile)) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						if (getSite() == null)
							return;
						if (getSite().getWorkbenchWindow() == null)
							return;
						IWorkbenchPage[] pages = getSite().getWorkbenchWindow()
								.getPages();
						for (int i = 0; i < pages.length; i++) {
							IEditorPart editorPart = pages[i].findEditor(input);
							pages[i].closeEditor(editorPart, true);
						}
					}
				});
			}
		}

		/*
		 * Closes all editors with this editor input on project close.
		 */
		final IResource res = event.getResource();
		if ((event.getType() == IResourceChangeEvent.PRE_CLOSE) || closeEditor) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					if (getSite() == null)
						return;
					if (getSite().getWorkbenchWindow() == null)
						return;
					IWorkbenchPage[] pages = getSite().getWorkbenchWindow()
							.getPages();
					for (int i = 0; i < pages.length; i++) {
						if (jmolfile.getProject().equals(res)) {
							IEditorPart editorPart = pages[i].findEditor(input);
							pages[i].closeEditor(editorPart, true);
						}
					}
				}
			});
		}
	}
}