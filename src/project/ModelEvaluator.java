package project;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import toberumono.structures.tuples.Pair;

public class ModelEvaluator {
	Pair<List<Pair<String, String>>, List<Double>> humanNNSeen;
	Pair<List<Pair<String, String>>, List<Double>> humanNNUnseen;
	Pair<List<Pair<String, String>>, List<Double>> humanANSeen;
	Pair<List<Pair<String, String>>, List<Double>> humanANUnseen;
	Pair<List<Pair<String, String>>, List<Double>> humanVNSeen;
	Pair<List<Pair<String, String>>, List<Double>> humanVNUnseen;
	
	void digestTestData(Path directory) {
		
	}
	
	Pair<List<Pair<String, String>>, List<Double>> digestTestData(Stream<String> pairs, Stream<String> means) {
		Stream<Pair<String, String>> paired = pairs.map(s -> s.split("\\s+")).map(s -> new Pair<>(s[0], s[1]));
		Stream<Double> meaned = means.map(s -> s.split("\\s+")).map(s -> s[3]).map(Double::parseDouble);
		return new Pair<>(paired.collect(Collectors.toList()), meaned.collect(Collectors.toList()));
	}
}
