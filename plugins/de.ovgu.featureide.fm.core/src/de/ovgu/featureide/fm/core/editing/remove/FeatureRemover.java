/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2015  FeatureIDE team, University of Magdeburg, Germany
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
package de.ovgu.featureide.fm.core.editing.remove;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.prop4j.And;
import org.prop4j.Literal;
import org.prop4j.Node;
import org.prop4j.Or;
import org.sat4j.specs.TimeoutException;

import de.ovgu.featureide.fm.core.editing.NodeCreator;
import de.ovgu.featureide.fm.core.editing.cnf.CNFSolver;
import de.ovgu.featureide.fm.core.editing.cnf.CNFSolver2;
import de.ovgu.featureide.fm.core.editing.cnf.Clause;
import de.ovgu.featureide.fm.core.editing.cnf.ICNFSolver;
import de.ovgu.featureide.fm.core.editing.cnf.UnkownLiteralException;
import de.ovgu.featureide.fm.core.editing.remove.DeprecatedFeatureMap.DeprecatedFeature;
import de.ovgu.featureide.fm.core.job.LongRunningMethod;
import de.ovgu.featureide.fm.core.job.WorkMonitor;

/**
 * Removes features from a model while retaining dependencies of all other feature.
 * 
 * @author Sebastian Krieter
 */
public class FeatureRemover implements LongRunningMethod<Node> {

	private final Node fmNode;

	private final Collection<String> features;

	private final boolean includeBooleanValues;
	private final boolean regularCNF;

	private List<DeprecatedClause> relevantClauseList;
	private Set<DeprecatedClause> relevantClauseSet;
	private Set<DeprecatedClause> newClauseSet;
	private Set<String> retainedFeatures;
	private Map<Object, Integer> idMap;

	private String[] featureNameArray;
	private boolean[] removedFeatures;

	private ICNFSolver solver;

	private DeprecatedFeatureMap map;

	public FeatureRemover(Node cnf, Collection<String> features) {
		this(cnf, features, true, false);
	}

	public FeatureRemover(Node cnf, Collection<String> features, boolean includeBooleanValues) {
		this(cnf, features, includeBooleanValues, false);
	}

	public FeatureRemover(Node cnf, Collection<String> features, boolean includeBooleanValues, boolean regularCNF) {
		this.fmNode = cnf;
		this.features = features;
		this.includeBooleanValues = includeBooleanValues;
		this.regularCNF = regularCNF;
	}

	private void addLiteral(Set<String> retainedFeatures, Node orChild) {
		final Literal literal = (Literal) orChild;
		if (literal.var instanceof String) {
			retainedFeatures.add((String) literal.var);
		}
	}

	private void collectFeatures() {
		if (fmNode instanceof And) {
			for (Node andChild : fmNode.getChildren()) {
				if (andChild instanceof Or) {
					for (Node orChild : andChild.getChildren()) {
						addLiteral(retainedFeatures, orChild);
					}
				} else {
					addLiteral(retainedFeatures, andChild);
				}
			}
		} else if (fmNode instanceof Or) {
			for (Node orChild : fmNode.getChildren()) {
				addLiteral(retainedFeatures, orChild);
			}
		} else {
			addLiteral(retainedFeatures, fmNode);
		}

		assert (retainedFeatures.containsAll(features));
	}

	private void init() {
		retainedFeatures = new HashSet<>();

		collectFeatures();

		removedFeatures = new boolean[retainedFeatures.size() + 1];

		featureNameArray = new String[retainedFeatures.size() + 1];
		idMap = new HashMap<>(retainedFeatures.size() << 1);
		{
			int id = 1;
			for (String name : features) {
				idMap.put(name, id);
				featureNameArray[id] = name;
				id++;
			}

			retainedFeatures.removeAll(features);

			for (String name : retainedFeatures) {
				idMap.put(name, id);
				featureNameArray[id] = name;
				id++;
			}
		}
	}

	public Node execute(WorkMonitor workMonitor) throws TimeoutException, UnkownLiteralException {
		// Collect all features in the prop node and remove TRUE and FALSE
		init();

		// CNF with more than one clause
		if (fmNode instanceof And) {
			return handleComplexFormula(workMonitor);
		} else if (fmNode instanceof Or) {
			return handleSingleClause(workMonitor);
		} else {
			return handleSingleLiteral(workMonitor);
		}
	}

	public Node handleSingleClause(WorkMonitor workMonitor) throws TimeoutException, UnkownLiteralException {
		for (Node clauseChildren : fmNode.getChildren()) {
			final Literal literal = (Literal) clauseChildren;
			if (features.contains(literal.var)) {
				return includeBooleanValues ? (regularCNF ? new Or(new Literal(NodeCreator.varTrue, true)) : new Literal(NodeCreator.varTrue, true))
						: new And();
			}
		}
		return fmNode.clone();
	}

	public Node handleSingleLiteral(WorkMonitor workMonitor) throws TimeoutException, UnkownLiteralException {
		return (features.contains(((Literal) fmNode).var)) ? (includeBooleanValues ? new Literal(NodeCreator.varTrue) : new And()) : fmNode.clone();
	}

	public Node handleComplexFormula(WorkMonitor workMonitor) throws TimeoutException, UnkownLiteralException {
		final Node[] andChildren = fmNode.getChildren();

		relevantClauseList = new ArrayList<>(andChildren.length);
		relevantClauseSet = new HashSet<>(andChildren.length << 1);
		newClauseSet = new HashSet<>(andChildren.length << 1);
		map = new DeprecatedFeatureMap(features, idMap);

		// fill sets
		createClauseList(andChildren);

		createSolver();

		workMonitor.setMaxAbsoluteWork(map.size() + 1);

		final double localFactor = 1.2;
		final double globalFactor = 1.5;

		final int maxClauseCountLimit = (int) Math.floor(globalFactor * relevantClauseList.size());
		int relevantIndex = 0;

		while (!map.isEmpty()) {
			if (workMonitor.step()) {
				return null;
			}
			final DeprecatedFeature next = map.next();
			final String curFeature = next.getFeature();
			final int curFeatureID = idMap.get(curFeature);

			final long estimatedClauseCount = next.getClauseCount();
			final int curClauseCountLimit = (int) Math.floor(localFactor * (relevantClauseList.size() - relevantIndex));

			if ((estimatedClauseCount > maxClauseCountLimit) || (estimatedClauseCount > curClauseCountLimit)) {
				//			if ((next.getClauseCount() > 1000)) {
//				relevantIndex = detectRedundantConstraintsSimple(relevantIndex);
				relevantIndex = detectRedundantConstraintsComplex(relevantIndex);
			}

			removeOldClauses(relevantIndex);

			// ... create list of clauses that contain this feature
			final byte[] clauseStates = new byte[relevantClauseList.size()];
			relevantIndex = sortRelevantList(curFeatureID, clauseStates);

			// Remove variable
			//generalize(curFeatureID, relevantIndex, clauseStates);
			resolution(curFeatureID, relevantIndex, clauseStates);

			idMap.remove(curFeature);
			removedFeatures[curFeatureID] = true;

			final int globalMixedClauseCount = map.getGlobalMixedClauseCount();
			if (globalMixedClauseCount == 0) {
				break;
			}
		}

		release();

		// create new clauses list
		final Node[] newClauses = createNewClauseList();
		workMonitor.step();

		return new And(newClauses);
	}

	private void release() {
		relevantClauseList.clear();
		relevantClauseSet.clear();
		solver.reset();

		solver = null;
		relevantClauseList = null;
		relevantClauseSet = null;
		removedFeatures = null;
	}

	private int detectRedundantConstraintsComplex(int relevantIndex) {
		createOrgSolver();
		for (int i = relevantClauseList.size() - 1; i >= relevantIndex; i--) {
			final DeprecatedClause mainClause = relevantClauseList.get(i);
			if (remove(mainClause)) {
				Collections.swap(relevantClauseList, i, relevantIndex);
				relevantIndex++;
				i++;
			} else {
				solver.addClause(mainClause);
			}
		}
		return relevantIndex;
	}

	private int detectRedundantConstraintsSimple(int relevantIndex) {
		for (int i = relevantClauseList.size() - 1; i >= relevantIndex; i--) {
			final DeprecatedClause mainClause = relevantClauseList.get(i);
			for (int j = i - 1; j >= relevantIndex; j--) {
				final DeprecatedClause subClause = relevantClauseList.get(j);
				final Clause contained = Clause.contained(mainClause, subClause);
				if (contained != null) {
					if (subClause == contained) {
						Collections.swap(relevantClauseList, j, relevantIndex);
						relevantIndex++;
					} else {
						Collections.swap(relevantClauseList, i, relevantIndex);
						relevantIndex++;
						i++;
						break;
					}
				}
			}
		}
		return relevantIndex;
	}

	private void removeOldClauses(int relevantIndex) {
		final List<DeprecatedClause> subList = relevantClauseList.subList(0, relevantIndex);
		relevantClauseSet.removeAll(subList);
		for (DeprecatedClause clause : subList) {
			clause.delete(map);
		}
		subList.clear();
	}

	private int sortRelevantList(int curFeatureID, byte[] clauseStates) {
		int relevantIndex = 0;
		for (int i = 0; i < relevantClauseList.size(); i++) {
			final Clause clause = relevantClauseList.get(i);
			for (int literal : clause.getLiterals()) {
				if (Math.abs(literal) == curFeatureID) {
					clauseStates[relevantIndex] = (byte) (literal > 0 ? 1 : 2);
					Collections.swap(relevantClauseList, i, relevantIndex);
					relevantIndex++;
					break;
				}
			}
		}
		return relevantIndex;
	}

	private void resolution(final int curFeatureID, int relevantIndex, final byte[] clauseStates) {
		for (int i = relevantIndex - 1; i >= 0; i--) {
			final boolean positive;
			switch (clauseStates[i]) {
			case 1:
				positive = true;
				break;
			case 2:
				positive = false;
				break;
			case -1:
			case 0:
			default:
				continue;
			}
			final int[] orChildren = relevantClauseList.get(i).getLiterals();
			{
				for (int j = i - 1; j >= 0; j--) {
					if ((positive && clauseStates[j] == 2) || (!positive && clauseStates[j] == 1)) {
						final int[] children2 = relevantClauseList.get(j).getLiterals();
						final int[] newChildren = new int[orChildren.length + children2.length];

						System.arraycopy(orChildren, 0, newChildren, 0, orChildren.length);
						System.arraycopy(children2, 0, newChildren, orChildren.length, children2.length);

						addNewClause(DeprecatedClause.createClause(map, newChildren, curFeatureID));
					}
				}
			}
		}
	}

	private void generalize(final int curFeatureID, int relevantIndex, final byte[] clauseStates) throws TimeoutException, UnkownLiteralException {
		for (int i = relevantIndex - 1; i >= 0; i--) {
			if (clauseStates[i] < 1) {
				continue;
			}

			final int[] orChildren = relevantClauseList.get(i).getLiterals();

			if (orChildren.length < 2) {
				continue;
			}

			final int[] literalList = new int[orChildren.length];

			int removeIndex = orChildren.length;
			int retainIndex = -1;

			for (int j = 0; j < orChildren.length; j++) {
				final int literal = orChildren[j];
				if (Math.abs(literal) == curFeatureID) {
					literalList[--removeIndex] = literal;
				} else {
					literalList[++retainIndex] = -literal;
				}
			}

			if (!solver.isSatisfiable(literalList)) {
				int[] retainLiterals = new int[retainIndex + 1];
				for (int j = 0; j < retainLiterals.length; j++) {
					retainLiterals[j] = -literalList[j];
				}

				addNewClause(DeprecatedClause.createClause(map, retainLiterals));

				clauseStates[i] = -1;
			}
		}
	}

	private void createClauseList(final Node[] andChildren) {
		for (int i = 0; i < andChildren.length; i++) {
			Node andChild = andChildren[i];

			final DeprecatedClause curClause;

			if (andChild instanceof Or) {
				int absoluteValueCount = 0;
				boolean valid = true;

				final Literal[] children = Arrays.copyOf(andChild.getChildren(), andChild.getChildren().length, Literal[].class);
				for (int j = 0; j < children.length; j++) {
					final Literal literal = children[j];

					// sort out obvious tautologies
					if (literal.var.equals(NodeCreator.varTrue)) {
						if (literal.positive) {
							valid = false;
						} else {
							absoluteValueCount++;
							children[j] = null;
						}
					} else if (literal.var.equals(NodeCreator.varFalse)) {
						if (literal.positive) {
							absoluteValueCount++;
							children[j] = null;
						} else {
							valid = false;
						}
					}
				}

				if (valid) {
					if (absoluteValueCount > 0) {
						if (children.length == absoluteValueCount) {
							throw new RuntimeException("Model is void!");
						}
						Literal[] newChildren = new Literal[children.length - absoluteValueCount];
						int k = 0;
						for (int j = 0; j < children.length; j++) {
							final Literal literal = children[j];
							if (literal != null) {
								newChildren[k++] = literal;
							}
						}
						curClause = DeprecatedClause.createClause(map, convert(newChildren));
					} else {
						curClause = DeprecatedClause.createClause(map, convert(children));
					}
				} else {
					curClause = null;
				}
			} else {
				final Literal literal = (Literal) andChild;
				if (literal.var.equals(NodeCreator.varTrue)) {
					if (!literal.positive) {
						throw new RuntimeException("Model is void!");
					}
					curClause = null;
				} else if (literal.var.equals(NodeCreator.varFalse)) {
					if (literal.positive) {
						throw new RuntimeException("Model is void!");
					}
					curClause = null;
				} else {
					curClause = DeprecatedClause.createClause(map, convert(new Literal[] { literal }));
				}
			}

			addNewClause(curClause);
		}
		// origClauseList = new ArrayList<>(newClauseSet);
	}

	private Node[] createNewClauseList() {
		final int newClauseSize = newClauseSet.size();
		final Node[] newClauses;
		if (includeBooleanValues) {
			newClauses = new Node[newClauseSize + 3];

			// create clause that contains all retained features
			final Node[] allLiterals = new Node[retainedFeatures.size() + 1];
			int i = 0;
			for (String featureName : retainedFeatures) {
				allLiterals[i++] = new Literal(featureName);
			}
			allLiterals[i] = new Literal(NodeCreator.varTrue);

			newClauses[newClauseSize] = new Or(allLiterals);
			if (regularCNF) {
				newClauses[newClauseSize + 1] = new Or(new Literal(NodeCreator.varTrue, true));
				newClauses[newClauseSize + 2] = new Or(new Literal(NodeCreator.varFalse, false));
			} else {
				newClauses[newClauseSize + 1] = new Literal(NodeCreator.varTrue, true);
				newClauses[newClauseSize + 2] = new Literal(NodeCreator.varFalse, false);
			}
		} else {
			newClauses = new Node[newClauseSize];
		}
		int j = 0;
		for (Clause newClause : newClauseSet) {
			final int[] newClauseLiterals = newClause.getLiterals();
			final Literal[] literals = new Literal[newClauseLiterals.length];
			int i = literals.length;
			for (int k = 0; k < literals.length; k++) {
				final int child = newClauseLiterals[k];
				literals[--i] = new Literal(featureNameArray[Math.abs(child)], child > 0);
			}
			newClauses[j++] = new Or(literals);
		}
		return newClauses;
	}

	private void createSolver() {
		if (solver != null) {
			solver.reset();
		}
		final List<Clause> clauseList = new ArrayList<>(relevantClauseList.size() + newClauseSet.size());
		clauseList.addAll(relevantClauseList);
		clauseList.addAll(newClauseSet);
		solver = new CNFSolver(clauseList, idMap.size());
	}

	private void createOrgSolver() {
		if (solver != null) {
			solver.reset();
		}
		final List<DeprecatedClause> clauseList = new ArrayList<>(newClauseSet);
		solver = new CNFSolver2(clauseList, removedFeatures);
	}

	private int[] convert(Literal[] newChildren) {
		final int[] literals = new int[newChildren.length];
		for (int j = 0; j < newChildren.length; j++) {
			final Literal child = newChildren[j];
			literals[j] = child.positive ? idMap.get(child.var) : -idMap.get(child.var);
		}
		return literals;
	}

	private void addNewClause(final DeprecatedClause curClause) {
		if (curClause != null) {
			if (curClause.getRelevance() == 0) {
				if (!newClauseSet.add(curClause)) {
					curClause.delete(map);
				}
			} else {
				if (relevantClauseSet.add(curClause)) {
					relevantClauseList.add(curClause);
				} else {
					curClause.delete(map);
				}
			}
		}
	}

	private boolean remove(DeprecatedClause curClause) {
		final int[] literals = curClause.getLiterals();
		final int[] literals2 = new int[literals.length];
		for (int i = 0; i < literals.length; i++) {
			literals2[i] = -literals[i];
		}
		boolean remove = false;
		try {
			remove = !solver.isSatisfiable(literals2);
		} catch (TimeoutException | UnkownLiteralException e) {
			e.printStackTrace();
		}
		return remove;
	}

}
