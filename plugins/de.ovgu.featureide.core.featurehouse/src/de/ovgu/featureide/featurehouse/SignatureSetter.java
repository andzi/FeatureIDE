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
package de.ovgu.featureide.featurehouse;

import AST.Program;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.fstmodel.FSTModel;
import de.ovgu.featureide.core.signature.ProjectSignatures;
import de.ovgu.featureide.fm.core.job.IJob;
import de.ovgu.featureide.fm.core.job.IJob.JobStatus;
import de.ovgu.featureide.fm.core.job.util.JobFinishListener;

/**
 * Can be run after fuji type checking to attach {@link ProjectSignatures} to an {@link FSTModel}.
 * 
 * @author Sebastian Krieter
 */
public class SignatureSetter implements JobFinishListener<Object> {
	private final FujiSignaturesCreator sigCreator = new FujiSignaturesCreator();
	
	private FSTModel fstModel = null;
	private ProjectSignatures signatures = null;
	
	private IFeatureProject fp = null;
	private Program ast = null;
	
	public void setFstModel(FSTModel fstModel) {
		synchronized (this) {
			this.fstModel = fstModel;
			if (signatures != null) {
				assignSignatures();
			}
		}
	}
	
	public void setFujiParameters(IFeatureProject fp, Program ast) {
		this.ast = ast;
		this.fp = fp;
	}

	@Override
	public void jobFinished(IJob<Object> finishedJob) {
		if (finishedJob.getStatus() == JobStatus.OK) {
			ProjectSignatures sigs = sigCreator.createSignatures(fp, ast);
			synchronized (this) {
				this.signatures = sigs;
				if (fstModel != null) {
					assignSignatures();
				}
			}
		}
	}
	
	private void assignSignatures() {
		sigCreator.attachJavadocComments(signatures, fstModel);
		fstModel.setProjectSignatures(signatures);
	}
}
