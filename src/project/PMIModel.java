package project;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;

public class PMIModel extends Model {
	
	public PMIModel(boolean requireSynchronized) {
		super(requireSynchronized);
	}
	
	public PMIModel(Path root, boolean requireSynchronized) throws IOException {
		super(root, requireSynchronized);
	}
	
	@Override
	protected void doSmoothing() {
		counts.keySet().stream().map(mapping -> pool.submit(() -> {
			String gov = mapping + "_g";
			for (Entry<String, Double> entry : probs.get(mapping).entrySet())
				entry.setValue(entry.getValue() / probs.get(gov).get(splitKey(entry.getKey()).group(1)));
		})).collect(Collectors.toList()).forEach(f -> {
			try {
				f.get();
			}
			catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});
		//Normalize
		counts.keySet().stream().map(mapping -> pool.submit(() -> {
			double total = 0.0;
			for (Double value : probs.get(mapping).values())
				total += value;
			if (total < 0)
				System.err.println("Failed");
			for (Entry<String, Double> entry : probs.get(mapping).entrySet())
				entry.setValue(entry.getValue() / total);
		})).collect(Collectors.toList()).forEach(f -> {
			try {
				f.get();
			}
			catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});
		counts.keySet().stream().map(mapping -> pool.submit(() -> {
			String dep = mapping + "_d";
			for (Entry<String, Double> entry : probs.get(mapping).entrySet())
				entry.setValue(entry.getValue() / probs.get(dep).get(splitKey(entry.getKey()).group(4)));
		})).collect(Collectors.toList()).forEach(f -> {
			try {
				f.get();
			}
			catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});
		//Normalize
		counts.keySet().stream().map(mapping -> pool.submit(() -> {
			double total = 0.0;
			for (Double value : probs.get(mapping).values())
				total += value;
			if (total < 0)
				System.err.println("Failed");
			for (Entry<String, Double> entry : probs.get(mapping).entrySet())
				entry.setValue(entry.getValue() / total);
		})).collect(Collectors.toList()).forEach(f -> {
			try {
				f.get();
			}
			catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		});
	}
	
	public static void main(String[] args) throws IOException {
		Model model = new PMIModel(Paths.get(args[0]), false);
		model.convertToProbs();
		model.smooth();
		model.storeModel(Paths.get(args[1]));
	}
}
