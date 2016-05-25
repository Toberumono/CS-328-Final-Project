package project;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import toberumono.structures.tuples.Triple;

public class LiModel extends Model{
	//Pattern keyExtractor = Pattern.compile("(\\w+) :: (\\w+) ~ (\\w+) :: (\\w+)");

	public LiModel(boolean requireSynchronized) {
		super(requireSynchronized);
	}


	public Map<String, Triple<List<String>, List<String>, int[][]>> getMatrices() {
		Map<String, Triple<List<String>, List<String>, int[][]>> out = new HashMap<>();
		for (String mapping : counts.keySet()) {
			List<String> govSet = probs.get(mapping + "_g").keySet().stream().collect(Collectors.toList());
			List<String> depSet = probs.get(mapping + "_d").keySet().stream().collect(Collectors.toList());
			Map<String, Double> pairs = probs.get(mapping);
			int totalCount = counts.get(mapping);
			
			int[][] counts = new int[govSet.size()][depSet.size()];
			String key;
			for (int i = 0; i < govSet.size(); i++) {
				for (int j = 0; j < depSet.size(); j++) {
					key = govSet.get(i) + " ~ " + depSet.get(j);
					counts[i][j] = (int) (pairs.containsKey(key) ? pairs.get(key) * totalCount : 0);
				}
			}
			out.put(mapping, new Triple<>(govSet, depSet, counts)); //x, y, z
		}
		return out;
	}
	
	
	@Override
	protected void smooth() {
		// TODO Auto-generated method stub
		
	}
	
	
}
