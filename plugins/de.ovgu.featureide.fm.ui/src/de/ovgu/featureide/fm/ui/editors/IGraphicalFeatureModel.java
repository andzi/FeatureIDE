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
package de.ovgu.featureide.fm.ui.editors;

import java.util.Collection;
import java.util.List;

import de.ovgu.featureide.fm.core.IGraphicItem;
import de.ovgu.featureide.fm.core.base.IConstraint;
import de.ovgu.featureide.fm.core.base.IFeature;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import de.ovgu.featureide.fm.ui.editors.featuremodel.layouts.FeatureModelLayout;

/**
 * Graphical representation of a feature model.
 * 
 * @author Sebastian Krieter
 */
public interface IGraphicalFeatureModel extends IGraphicItem, Cloneable {

	IFeatureModel getFeatureModel();

	FeatureModelLayout getLayout();

	void handleLegendLayoutChanged();

	void handleModelLayoutChanged();

	void redrawDiagram();

	void refreshContextMenu();
	
	Collection<IGraphicalFeature> getFeatures();

	IGraphicalFeature getGraphicalFeature(IFeature newFeature);

	List<IGraphicalConstraint> getConstraints();
	
	IGraphicalConstraint getGraphicalConstraint(IConstraint newFeature);
		
	IGraphicalFeatureModel clone();
	
	void init();

}
