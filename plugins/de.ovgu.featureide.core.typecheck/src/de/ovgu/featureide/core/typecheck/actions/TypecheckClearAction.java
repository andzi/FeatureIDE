package de.ovgu.featureide.core.typecheck.actions;

import java.util.Arrays;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.typecheck.TypeCheckerFIDE;
import de.ovgu.featureide.core.typecheck.TypecheckCorePlugin;
import de.ovgu.featureide.core.typecheck.correction.FIDEProblemHandler;

public class TypecheckClearAction implements IObjectActionDelegate {

    private IStructuredSelection selection;

    @Override
    public void run(IAction action) {
	Object obj = selection.getFirstElement();
	if (obj instanceof IResource) {
	    IResource res = (IResource) obj;

	    IFeatureProject project = CorePlugin.getFeatureProject(res);

	    if (Arrays.asList(TypecheckCorePlugin.supportedComposers).contains(
		    project.getComposerID())) {

		try {
		    project.getSourceFolder().deleteMarkers(
			    TypeCheckerFIDE.CHECK_MARKER, false,
			    IResource.DEPTH_INFINITE);
		} catch (CoreException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
		}

	    } else {
		// TODO: change output method
		System.out.println("unsupported composer found");
	    }
	}
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
	this.selection = (IStructuredSelection) selection;
    }

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	// TODO Auto-generated method stub

    }
}
