package project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
	
	public Map<String, Triple<List<String>, List<Set<String>>, int[][]>> getMatrices() {
		Map<String, Triple<List<String>, List<Set<String>>, int[][]>> out = new HashMap<>();
		for (String mapping : counts.keySet()) {
			List<String> govSet = probs.get(mapping + "_g").keySet().stream().collect(Collectors.toList());
			List<Set<String>> depSet = probs.get(mapping + "_d").keySet().stream().map(s -> {
				Set<String> set = new HashSet<>();
				set.add(s);
				return set;
			}).collect(Collectors.toList());
			Map<String, Double> pairs = probs.get(mapping);
			int totalCount = counts.get(mapping);
			
			int[][] counts = new int[depSet.size()][govSet.size()];
			String key;
			for (int i = 0; i < govSet.size(); i++) {
				for (int j = 0; j < depSet.size(); j++) {
					key = govSet.get(i) + " ~ " + depSet.get(j);
					counts[j][i] = (int) (pairs.containsKey(key) ? pairs.get(key) * totalCount : 0); //j, i because this needs to have the noun in the first index
				}
			}
			out.put(mapping, new Triple<>(govSet, depSet, counts)); //x, y, z
		}
		return out;
	}
	
	public void performLDatSimplification(List<String> govs, List<Set<String>> deps, int[][] counts, int V, int I, int N) {
		List<Pair<int[], Double>> ldat = new SortedList<>((a, b) -> a.getY().compareTo(b.getY())); //int[] = {i, j}
		while (true) {
			int[] sums = new int[counts.length]; //f(C_x)
			int m = I * V;
			double threshold = Math.log(m) / 2 * m;
			for (int i = 0; i < I; i++)
				for (int v = 0; v < V; v++)
					sums[i] += counts[i][v];
			for (int i = 0; i < I; i++) {
				for (int j = i + 1; j < I; j++) {
					double sum = 0;
					for (int v = 0; v < V; v++) {
						sum += counts[i][v] * Math.log(((double) counts[i][v]) / sums[i]);
						sum += counts[j][v] * Math.log(((double) counts[j][v]) / sums[j]);
						sum += (counts[i][v] + counts[j][v]) * Math.log((((double) counts[i][v]) + ((double) counts[j][v])) / (sums[i] + sums[j]));
					}
					if (sum < threshold)
						ldat.add(new Pair<>(new int[]{i, j}, sum));
				}
			}
			if (ldat.size() < 1)
				break;
			//Merge
			int[] ij;
			for (int n = 0; n < N; n++) {
				ij = ldat.get(n).getX();
				for (int v = 0; v < V; v++) {
					counts[ij[0]][v] += counts[ij[1]][v];
					counts[ij[1]][v] = counts[I - 1][v];
				}
				sums[ij[0]] += sums[ij[1]];
				sums[ij[1]] = sums[I - 1];
				deps.get(ij[0]).addAll(deps.get(ij[1]));
				deps.set(ij[1], deps.remove(I - 1));
				I--;
			}
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
			Map<String, Triple<List<String>, List<Set<String>>, int[][]>> matrices = getMatrices();
			for (Entry<String, Triple<List<String>, List<Set<String>>, int[][]>> entry : matrices.entrySet()) {
				List<String> govs = entry.getValue().getX();
				List<Set<String>> deps = entry.getValue().getY();
				int[][] counts = entry.getValue().getZ();
				performLDatSimplification(govs, deps, counts, govs.size(), deps.size(), 1);
				for (int i = 0; i < counts.length; i++) { //deps
					for (int j = 0; j < counts[i].length; j++) { //govs
						double prob = counts[i][j] / ((double) deps.get(i).size());
						for (String dep : deps.get(i))
							probs.get(entry.getKey()).put(govs.get(i) + " ~ " + dep, prob);
					}
				}
			}
			smoothed = true;
		}
	}
}
