/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2016  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 * 
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */
package de.ovgu.featureide.fm.ui.editors.featuremodel.actions;

import static de.ovgu.featureide.fm.core.localization.StringTable.COLLAPSE_SIBLINGS;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.gef.ui.parts.GraphicalViewerImpl;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.PlatformUI;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.ui.FMUIPlugin;
import de.ovgu.featureide.fm.ui.editors.featuremodel.editparts.FeatureEditPart;
import de.ovgu.featureide.fm.ui.editors.featuremodel.operations.SetFeaturesToCollapsedOperation;

/**
 * Collapses all siblings of the selected feature if the parent is either an OR or an ALTERNATIVE.
 * 
 * @author Maximilian K�hl
 */
public class CollapseFeaturesAction extends SingleSelectionAction {

	public static final String ID = "de.ovgu.featureide.collapsefeatures";

	private IFeatureModel featureModel;

	private ISelectionChangedListener listener = new ISelectionChangedListener() {
		public void selectionChanged(SelectionChangedEvent event) {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			setEnabled(isValidSelection(selection));
			if (isValidSelection(selection)) {
			if (selection.getFirstElement() instanceof FeatureEditPart) {
				if (getSelectedFeature().getStructure().getParent().isAnd()) {
					setEnabled(false);
				} else {
					setEnabled(true);
				}
			}
			}
			//				if (isValidSelection(selection)) {
			//					if (selection.getFirstElement() instanceof FeatureEditPart || selection.getFirstElement() instanceof IFeature) {
			//						setEnabled(true);
			//					} else {
			//						setEnabled(false);
			//					}
			//				} else {
			//					setEnabled(false);
			//				}
		}
	};

	/**
	 * @param label
	 *            Description of this operation to be used in the menu
	 * @param feature
	 *            feature on which this operation will be executed
	 * 
	 */
	public CollapseFeaturesAction(Object viewer, IFeatureModel featureModel) {
		super(COLLAPSE_SIBLINGS, viewer);
		this.featureModel = featureModel;
		setEnabled(false);
		if (viewer instanceof GraphicalViewerImpl) {
			((GraphicalViewerImpl) viewer).addSelectionChangedListener(listener);
		} else {
			((TreeViewer) viewer).addSelectionChangedListener(listener);
		}
	}

	@Override
	public void run() {

		//			setChecked(feature.getStructure().getParent().getChildren().isCollapsed());
		SetFeaturesToCollapsedOperation op = new SetFeaturesToCollapsedOperation(feature, featureModel);

		try {
//			PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().execute(op, null, null);
			op.execute(null, null);
		} catch (ExecutionException e) {
			FMUIPlugin.getDefault().logError(e);

		}

	}

	/* (non-Javadoc)
	 * @see de.ovgu.featureide.fm.ui.editors.featuremodel.actions.SingleSelectionAction#updateProperties()
	 */
	@Override
	protected void updateProperties() {
		//			setEnabled(feature.getStructure().getParent().isAlternative() || feature.getStructure().getParent().isOr());
	}
}