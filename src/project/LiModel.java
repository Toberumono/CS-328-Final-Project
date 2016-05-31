package project;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.tuples.Pair;
import toberumono.structures.tuples.Triple;

public class LiModel extends Model {
	//Pattern keyExtractor = Pattern.compile("(\\w+) :: (\\w+) ~ (\\w+) :: (\\w+)");
	private boolean smoothed;
	
	public LiModel(boolean requireSynchronized) {
		super(requireSynchronized);
	}
	
	public LiModel(Path root, boolean requireSynchronized) throws IOException {
		super(root, requireSynchronized);
	}
	
	private class MatrixIterator implements Iterator<Pair<String, Triple<List<List<String>>, List<List<String>>, int[][]>>> {
		private final Iterator<String> mappings;
		
		public MatrixIterator() {
			mappings = counts.keySet().iterator();
		}
		
		@Override
		public boolean hasNext() {
			return mappings.hasNext();
		}
		
		@Override
		public Pair<String, Triple<List<List<String>>, List<List<String>>, int[][]>> next() {
			String mapping = mappings.next();
			List<List<String>> govSet = probs.get(mapping + "_g").keySet().stream().map(s -> {
				List<String> set = new ArrayList<>();
				set.add(s);
				return set;
			}).collect(Collectors.toList());
			List<List<String>> depSet = probs.get(mapping + "_d").keySet().stream().map(s -> {
				List<String> set = new ArrayList<>();
				set.add(s);
				return set;
			}).collect(Collectors.toList());
			Map<String, Double> pairs = probs.get(mapping);
			int totalCount = counts.get(mapping);
			
			System.out.println(mapping + ": " + ((long) depSet.size()) * ((long) govSet.size()));
			int[][] counts = new int[depSet.size()][govSet.size()];
			String key;
			for (int i = 0; i < govSet.size(); i++) {
				for (int j = 0; j < depSet.size(); j++) {
					key = govSet.get(i).get(0) + " ~ " + depSet.get(j).get(0);
					counts[j][i] = (int) (pairs.containsKey(key) ? pairs.get(key) * totalCount : 0); //j, i because this needs to have the noun in the first index
				}
			}
			return new Pair<>(mapping, new Triple<>(govSet, depSet, counts)); //x, y, z
		}
	}
	
	public void performLDatSimplification(List<List<String>> govs, List<List<String>> deps, int[][] counts, int V, int I, int N) {
		SortedList<Pair<int[], Double>> ldat = new SortedList<>((a, b) -> a.getY().compareTo(b.getY())); //int[] = {i, j}
		List<int[]> sortedJ = new SortedList<>((a, b) -> a[1] < b[1] ? 1 : (a[1] == b[1] ? 0 : -1));
		int[] sumsI = new int[I], sumsV = new int[V]; //f(C_x)
		for (int i = 0; i < I; i++)
			for (int v = 0; v < V; v++)
				sumsI[i] += counts[i][v];
		for (int i = 0; i < V; i++)
			for (int v = 0; v < I; v++)
				sumsV[i] += counts[v][i];
		int m, index;
		double threshold, sum;
		Pair<int[], Double> pair;
		boolean changed = true;
		while (changed) {
			changed = false;
			m = I + V;
			threshold = Math.log(m) / 2;
			System.out.println(threshold);
			for (int i = 0; i < I; i++) {
				for (int j = i + 1; j < I; j++) {
					sum = 0;
					int prev = ldat.size();
					for (int v = 0; v < V; v++) {
						sum += counts[i][v] != 0 ? counts[i][v] * Math.log(((double) counts[i][v]) / sumsI[i]) : 0;
						sum += counts[j][v] != 0 ? counts[j][v] * Math.log(((double) counts[j][v]) / sumsI[j]) : 0;
						sum -= counts[i][v] != 0 || counts[j][v] != 0 ? (counts[i][v] + counts[j][v]) * Math.log((((double) counts[i][v]) + ((double) counts[j][v])) / (sumsI[i] + sumsI[j])) : 0;
					}
					additionAttempt: if (sum > 0 && sum < threshold) {
						pair = new Pair<>(new int[]{i, j}, sum);
						index = ldat.insertionIndexOf(pair);
						for (int k = 0; k < index; k++)
							if (ldat.get(k).getX()[1] == pair.getX()[1])
								break additionAttempt;
						ldat.add(pair);
						for (int k = index + 1; k < ldat.size(); k++)
							if (ldat.get(k).getX()[1] == pair.getX()[1])
								ldat.remove(k--);
					}
					if (prev > 0 && ldat.size() == 0)
						System.out.println("Problem: " + i + ", " + j);
					while (ldat.size() > N)
						ldat.remove(ldat.size() - 1);
				}
			}
			System.out.println("ldat: " + ldat.size());
			if (ldat.size() > 0)
				changed = true;
			//Merge
			ldat.forEach(e -> sortedJ.add(e.getX()));
			for (int[] ij : sortedJ) {
				if (ij[1] >= I)
					continue;
				for (int v = 0; v < V; v++) {
					counts[ij[0]][v] += counts[ij[1]][v];
					counts[ij[1]][v] = counts[I - 1][v];
				}
				sumsI[ij[0]] += sumsI[ij[1]];
				sumsI[ij[1]] = sumsI[I - 1];
				deps.get(ij[0]).addAll(deps.get(ij[1]));
				if (ij[1] < I - 1)
					deps.set(ij[1], deps.remove(I - 1));
				else
					deps.remove(I - 1);
				I--;
			}
			sortedJ.clear();
			ldat.clear();
			
			//LDAT PART 2
			
			m = I + V;
			threshold = Math.log(m) / 2;
			System.out.println(threshold);
			for (int i = 0; i < V; i++) {
				for (int j = i + 1; j < V; j++) {
					sum = 0;
					for (int v = 0; v < I; v++) {
						sum += counts[v][i] != 0 ? counts[v][i] * Math.log(((double) counts[v][i]) / sumsV[i]) : 0;
						sum += counts[v][j] != 0 ? counts[v][j] * Math.log(((double) counts[v][j]) / sumsV[j]) : 0;
						sum -= counts[v][i] != 0 || counts[v][j] != 0 ? (counts[v][i] + counts[v][j]) * Math.log((((double) counts[v][i]) + ((double) counts[v][j])) / (sumsV[i] + sumsV[j])) : 0;
					}
					additionAttempt: if (sum > 0 && sum < threshold) {
						pair = new Pair<>(new int[]{i, j}, sum);
						index = ldat.insertionIndexOf(pair);
						for (int k = 0; k < index; k++)
							if (ldat.get(k).getX()[1] == pair.getX()[1])
								break additionAttempt;
						ldat.add(pair);
						for (int k = index + 1; k < ldat.size(); k++)
							if (ldat.get(k).getX()[1] == pair.getX()[1])
								ldat.remove(k--);
					}
					while (ldat.size() > N)
						ldat.remove(ldat.size() - 1);
				}
			}
			System.out.println("ldat: " + ldat.size());
			if (ldat.size() > 0)
				changed = true;
			//Merge
			ldat.forEach(e -> sortedJ.add(e.getX()));
			for (int[] ij : sortedJ) {
				if (ij[1] >= V)
					continue;
				for (int v = 0; v < I; v++) {
					counts[v][ij[0]] += counts[v][ij[1]];
					counts[v][ij[1]] = counts[v][V - 1];
				}
				sumsV[ij[0]] += sumsV[ij[1]];
				sumsV[ij[1]] = sumsV[V - 1];
				govs.get(ij[0]).addAll(govs.get(ij[1]));
				if (ij[1] < V - 1)
					govs.set(ij[1], govs.remove(V - 1));
				else
					govs.remove(V - 1);
				V--;
			}
			sortedJ.clear();
			ldat.clear();
		}
	}
	
	@Override
	protected void smooth() { //We don't need to clear the probs map in this method because every value will be overwritten
		convertToProbs();
		if (smoothed)
			return;
		synchronized (this) {
			if (smoothed)
				return;
			new MatrixIterator().forEachRemaining(entry -> {
				List<List<String>> govs = entry.getY().getX();
				List<List<String>> deps = entry.getY().getY();
				int[][] counts = entry.getY().getZ();
				performLDatSimplification(govs, deps, counts, govs.size(), deps.size(), 50);
				System.out.println(govs.size() + ", " + deps.size());
				for (int i = 0; i < deps.size(); i++) { //deps
					for (int j = 0; j < govs.size(); j++) { //govs
						double prob = counts[i][j] / (((double) deps.get(i).size()) * ((double) govs.get(j).size()));
						Map<String, Double> map = probs.get(entry.getX());
						for (String dep : deps.get(i))
							for (String gov : govs.get(j))
								if (prob > 0)
									map.put(gov + " ~ " + dep, prob);
					}
				}
				System.out.println("Processed: " + entry.getX());
			});
			smoothed = true;
		}
	}
	
	public static void main(String[] args) throws IOException {
		LiModel model = new LiModel(Paths.get(args[0]), false);
		model.smooth();
		model.storeModel(Paths.get(args[1]));
	}
}
