package project;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.tuples.Pair;

public class ModelEvaluator {
	Pair<List<Pair<String, String>>, List<Double>> humanNNSeen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanNNUnseen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanANSeen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanANUnseen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanVNSeen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Pair<List<Pair<String, String>>, List<Double>> humanVNUnseen = new Pair<>(new ArrayList<>(), new ArrayList<>());
	Map<String, Double> modelNN = new HashMap<>();
	Map<String, Double> modelAN = new HashMap<>();
	Map<String, Double> modelVN = new HashMap<>();
	
	public void digestTestData(Path items, Path means) throws IOException {
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(items, path -> path.getFileName().toString().endsWith(".items"))) {
			for (Path p : ds) {
				String name = items.relativize(p).toString();
				Pair<List<Pair<String, String>>, List<Double>> data =
						digestTestData(Files.lines(items.resolve(name)), Files.lines(means.resolve(name.toString().substring(0, name.indexOf(".items")) + ".means")));
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
		Stream<Double> meaned = means.map(s -> s.split("\\s+")).map(s -> s[3]).map(Double::parseDouble).map(d -> Math.pow(10, d));
		return new Pair<>(paired.collect(Collectors.toList()), meaned.collect(Collectors.toList()));
	}
	
	public void ingestModel(Model model) {
		Pattern keyDecomposer = Pattern.compile("(.*?) :: (.*?) ~ (.*?) :: (.*)");
		Matcher parts;
		for (String mapping : model.counts.keySet()) {
			for (Entry<String, Double> e : model.probs.get(mapping).entrySet()) {
				parts = keyDecomposer.matcher(e.getKey());
				parts.find();
				if (!parts.group(4).equals("NN"))
					continue;
				if (parts.group(2).startsWith("JJ")) {
					if (!modelAN.containsKey(e.getKey()) || modelAN.get(e.getKey()) < e.getValue())
						modelAN.put(e.getKey(), e.getValue());
				}
				else if (parts.group(2).equals("NN")) {
					if (!modelNN.containsKey(e.getKey()) || modelNN.get(e.getKey()) < e.getValue())
						modelNN.put(e.getKey(), e.getValue());
				}
				else if (parts.group(2).startsWith("VB")) {
					if (!modelVN.containsKey(e.getKey()) || modelVN.get(e.getKey()) < e.getValue())
						modelVN.put(e.getKey(), e.getValue());
				}
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		ModelEvaluator me = new ModelEvaluator();
		me.digestTestData(Paths.get("./Keller_Lapata_2003_Plausibility_Data/items"), Paths.get("./Keller_Lapata_2003_Plausibility_Data/means"));
		Model model = new CutoffLexicalHierarchy(Paths.get(args[0]), false);
		me.ingestModel(model);
		double[][] xy;
		PearsonsCorrelation cor;
		
		xy = makeArrays("JJ", me.humanANSeen, model);
		if (xy.length > 2) {
			cor = new PearsonsCorrelation(xy);
			System.out.println("AN-Seen: " + (xy.length < 3 ? -1 : cor.getCorrelationPValues().getEntry(0, 1)));
		}
		xy = makeArrays("JJ", me.humanANUnseen, model);
		if (xy.length > 2) {
			cor = new PearsonsCorrelation(xy);
			System.out.println("AN-Unseen: " + (xy.length < 3 ? -1 : cor.getCorrelationPValues().getEntry(0, 1)));
		}
		
		xy = makeArrays("NN", me.humanNNSeen, model);
		if (xy.length > 2) {
			cor = new PearsonsCorrelation(xy);
			System.out.println("NN-Seen: " + (xy.length < 3 ? -1 : cor.getCorrelationPValues().getEntry(0, 1)));
		}
		xy = makeArrays("NN", me.humanNNUnseen, model);
		if (xy.length > 2) {
			cor = new PearsonsCorrelation(xy);
			System.out.println("NN-Unseen: " + (xy.length < 3 ? -1 : cor.getCorrelationPValues().getEntry(0, 1)));
		}
		
		xy = makeArrays("VB", me.humanVNSeen, model);
		if (xy.length > 2) {
			cor = new PearsonsCorrelation(xy);
			System.out.println("VB-Seen: " + (xy.length < 3 ? -1 : cor.getCorrelationPValues().getEntry(0, 1)));
		}
		xy = makeArrays("VB", me.humanVNUnseen, model);
		if (xy.length > 2) {
			cor = new PearsonsCorrelation(xy);
			System.out.println("VB-Unseen: " + (xy.length < 3 ? -1 : cor.getCorrelationPValues().getEntry(0, 1)));
		}
	}
	
	public static double[][] makeArrays(String firstPOS, Pair<List<Pair<String, String>>, List<Double>> human, Model model) {
		List<Double> x = new ArrayList<>(), y = new ArrayList<>();
		for (int i = 0; i < human.getX().size(); i++) {
			x.add(human.getY().get(i));
			y.add(model.getHighestProbabilityForBigram(human.getX().get(i).getX(), firstPOS, human.getX().get(i).getY(), "NN"));
		}
		double[][] out = new double[x.size()][2];
		for (int i = 0; i < out.length; i++) {
			out[i][0] = x.get(i);
			out[i][1] = y.get(i);
		}
		return out;
	}
	
	public List<Pair<int[], Double>> computeLdat(int[][] counts, int I, int V) {
		int[] sums = new int[counts.length]; //f(C_x)
		for (int i = 0; i < I; i++)
			for (int v = 0; v < V; v++)
				sums[i] += counts[i][v];
		List<Pair<int[], Double>> ldat = new SortedList<>((a, b) -> a.getY().compareTo(b.getY())); //int[] = {i, j}
		for (int i = 0; i < I; i++) {
			for (int j = i + 1; j < I; j++) {
				double sum = 0;
				for (int v = 0; v < V; v++) {
					sum += counts[i][v] * Math.log(((double) counts[i][v]) / sums[i]);
					sum += counts[j][v] * Math.log(((double) counts[j][v]) / sums[j]);
					sum += (counts[i][v] + counts[j][v]) * Math.log((((double) counts[i][v]) + ((double) counts[j][v])) / (sums[i] + sums[j]));
				}
				ldat.add(new Pair<>(new int[]{i, j}, sum));
			}
		}
		return ldat;
	}
}
