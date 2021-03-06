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
package de.ovgu.featureide.fm.core.base.impl;

import javax.annotation.CheckForNull;

import de.ovgu.featureide.fm.core.ExtensionManager;
import de.ovgu.featureide.fm.core.io.IPersistentFormat;

/**
 * Manages additional formats for a certain object (e.g., a feature model or configuration).
 * 
 * @author Sebastian Krieter
 */
public abstract class FormatManager<T extends IPersistentFormat<?>> extends ExtensionManager<T> {

	public T getFormatById(String id) throws NoSuchExtensionException {
		return getExtension(id);
	}

	@CheckForNull
	public T getFormatByExtension(String extension) {
		if (extension != null) {
			for (T format : getExtensions()) {
				if (extension.equals(format.getSuffix())) {
					return format;
				}
			}
		}
		return null;
	}

	@CheckForNull
	public T getFormatByFileName(String fileName) {
		return getFormatByExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
	}

}
