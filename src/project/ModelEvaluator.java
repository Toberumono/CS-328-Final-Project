package project;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import toberumono.structures.tuples.Pair;

public class ModelEvaluator {
	Pair<List<Pair<String, String>>, List<Double>> humanNNSeen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanNNUnseen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanANSeen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanANUnseen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanVNSeen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanVNUnseen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	
	public void digestTestData(Path items, Path means) throws IOException {
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(items, path -> path.getFileName().toString().endsWith(".items"))) {
			for (Path p : ds) {
				String name = items.relativize(p).toString();
				Pair<List<Pair<String, String>>, List<Double>> data = digestTestData(Files.lines(items.resolve(name)), Files.lines(means.resolve(name.toString().substring(0, name.indexOf(".items")) + ".means")));
				if (name.startsWith("A1"))
					humanANSeen = data;
				else if (name.startsWith("A2"))
					humanANUnseen = data;
				else if (name.startsWith("N1"))
					humanNNSeen = data;
				else if (name.startsWith("N2"))
					humanNNUnseen = data;
				else if (name.startsWith("V1"))
					humanVNSeen = data;
				else if (name.startsWith("V2"))
					humanVNUnseen = data;
			}
		}
	}
	
	public Pair<List<Pair<String, String>>, List<Double>> digestTestData(Stream<String> pairs, Stream<String> means) {
		Stream<Pair<String, String>> paired = pairs.map(s -> s.split("\\s+")).map(s -> new Pair<>(s[0], s[1]));
		Stream<Double> meaned = means.map(s -> s.split("\\s+")).map(s -> s[3]).map(Double::parseDouble);
		return new Pair<>(paired.collect(Collectors.toList()), meaned.collect(Collectors.toList()));
	}
	
	public static void main(String[] args) throws IOException {
		ModelEvaluator me = new ModelEvaluator();
		me.digestTestData(Paths.get("./Keller_Lapata_2003_Plausibility_Data/items"), Paths.get("./Keller_Lapata_2003_Plausibility_Data/means"));
	}
}
