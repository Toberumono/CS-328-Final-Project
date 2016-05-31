package project;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import toberumono.structures.tuples.Pair;
import toberumono.structures.tuples.Triple;

import edu.smu.tspell.wordnet.NounSynset;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import edu.stanford.nlp.trees.TypedDependency;

public class WordnetModel extends Model {
	private static final NounSynset[] A = null;
	private static WordNetDatabase wordnetDB = WordNetDatabase.getFileInstance();
	private static Map<String, List<Node>> NounTreeMap;
	private static Node wordnetNounTree;
	
	public static void main(String[] args) {
		try {
			NounSynset nounSynset;
			NounSynset[] hyponyms;
			NounSynset[] hypernyms;
			
			WordNetDatabase database = WordNetDatabase.getFileInstance();
			Synset[] synsets = database.getSynsets("airplane", SynsetType.NOUN);
			for (int i = 0; i < synsets.length; i++) {
				nounSynset = (NounSynset) (synsets[i]);
				hyponyms = nounSynset.getHyponyms();
				hypernyms = nounSynset.getHypernyms();
				String[] syns = nounSynset.getWordForms();
				for (String s : syns) {
					System.err.println("      " + s);
				}
				
				System.err.println(nounSynset.getWordForms().toString() +
						": " + nounSynset.getDefinition() + ") has " + hyponyms.length + " hyponyms");
				for (int j = 0; j < hyponyms.length; j++) {
					System.err.println(hyponyms[j].getWordForms()[0]);
				}
				System.err.println(nounSynset.getWordForms()[0] +
						": " + nounSynset.getDefinition() + ") has " + hypernyms.length + " hypernyms");
				for (int j = 0; j < hypernyms.length; j++) {
					System.err.println(hypernyms[j].getWordForms()[0]);
				}
			}
			System.err.println("Making a test for the thing\n\n");
			Synset[] A = database.getSynsets("dog", SynsetType.NOUN);
			Synset[] B = database.getSynsets("dog", SynsetType.NOUN);
			if (A[0].equals(B[0])) {
				System.err.println("Synsets are equal");
			}
			else {
				System.err.println("Synsets are not equal");
			}
			NounSynset[] s = ((NounSynset) A[0]).getHypernyms();
			for (NounSynset a : s) {
				System.err.println(a.getWordForms()[0] + a.getWordForms()[1]);
			}
			Synset[] test = database.getSynsets("canine", SynsetType.NOUN);
			System.err.println("Roots");
			
			WordnetModel m = new WordnetModel(Paths.get("/Users/jamie/Documents/College/Junior Year/Computational Cognition/CS328Final/BNC Digested/Corpus.medium"), false);
			System.out.println("develop: " + m.getGeneralization("direct object", "economy", "develop"));
			System.out.println("eat: " + m.getGeneralization("direct object", "chicken", "eat"));
			System.out.println("eat: " + m.getGeneralization("direct object", "economy", "eat"));
			System.out.println("justify: " + m.getGeneralization("nominal subject", "means", "justify"));
			System.out.println("justify: " + m.getGeneralization("nominal subject", "ends", "justify"));
			//System.out.println("justify: " + m.getGeneralization("nominal subject", "justify", "ends"));
			System.out.println("justify: " + m.getGeneralization("direct object", "means", "justify"));
			//m.initilzieWordnet();
			
		}
		catch (Exception e) {
			System.err.println("Error");
			e.printStackTrace();
		}
	}
	
	public void initilzieWordnet() throws InterruptedException {
		wordnetNounTree = new Node(wordnetDB.getSynsets("entity", SynsetType.NOUN)[0]);
		NounTreeMap = new HashMap<>();
		List<Node> root = new LinkedList<Node>();
		root.add(wordnetNounTree);
		NounTreeMap.put(wordnetNounTree.synset.getDefinition(), root);
		Queue<Node> nqueue = new LinkedBlockingQueue<>();
		nqueue.add(wordnetNounTree);
		
		while (!nqueue.isEmpty()) {
			Node current = nqueue.remove();
			NounSynset[] children = ((NounSynset) current.synset).getHyponyms();
			current.children = new Node[children.length];
			for (int i = 0; i < current.children.length; i++) {
				current.children[i] = new Node(current, children[i]);
				nqueue.add(current.children[i]);
				
				String tmp = children[i].getDefinition();
				if (NounTreeMap.containsKey(tmp)) {
					NounTreeMap.get(tmp).add(current.children[i]);
				}
				else {
					List<Node> newList = new LinkedList<Node>();
					newList.add(current.children[i]);
					NounTreeMap.put(tmp, newList);
				}
			}
		}
		System.err.println("Num Synsets: " + NounTreeMap.size());
		int[] treeinfo = initilizeSubtreesRecursive(wordnetNounTree);
		System.err.println("Finished adding subtree data to nodes");
		System.err.println("There are " + treeinfo[0] + " nodes in the tree with " + treeinfo[1] + " words total");
	}
	
	private int[] initilizeSubtreesRecursive(Node root) {
		int[] out = new int[2];
		out[0] = root.numNodes;
		out[1] = root.numNouns;
		for (Node c : root.children) {
			int[] tmp = initilizeSubtreesRecursive(c);
			out[0] += tmp[0];
			out[1] += tmp[1];
		}
		root.numNodes = out[0];
		root.numNouns = out[1];
		return out;
	}
	
	
	
	
	// For right now this is only for verbs, will generalize later
	public double getGeneralization(String marker, String nn, String vb) throws InterruptedException {
		convertToProbs();
		// List<Triple<Synset,Double>> out = new ArrayList<>();
		resetProbablities();
		
		// Counting all the instances of nouns in the tree
		List<Pair<String, Double>> usedIDs = new ArrayList<>();
		double sum = 0;
		String gov = generateKey(vb, "VB");
		for (Entry<String, Double> e : probs.get(marker).entrySet()) {
			if (e.getKey().startsWith(gov)) {
				if (NounTreeMap.get(splitKey(e.getKey()).group(3))!=null) {
					sum += e.getValue(); /// counts.get(marker)
					usedIDs.add(new Pair<>(e.getKey().substring(e.getKey().indexOf('~') + 2), e.getValue()));
				}
				
			}
		}
		System.out.println(sum);
		
		// Mapping those counts to the tree
		Set<Node> visited = new HashSet<>();
		for (Pair<String, Double> ID : usedIDs) {
			Double prob = ID.getY() / sum;
			Synset[] synsets = wordnetDB.getSynsets(ID.getX().substring(0, ID.getX().indexOf(" :: ")), SynsetType.NOUN);
			int totalinstances = 0;
			for (Synset s : synsets) {
				List<Node> tmp = NounTreeMap.get(s.getDefinition());
				if (tmp!=null) {
					totalinstances = totalinstances + tmp.size();
				} else {
					System.err.println(ID.getX());
				}
				
			}
			for (Synset s : synsets) {
				List<Node> tmp = NounTreeMap.get(s.getDefinition());
				if (tmp!=null) {
					for (Node n : tmp) {
						n.probability = n.probability + (prob / totalinstances);
						visited.add(n);
					}
				}
				
			}
			
		}
		
		// Collapsing probabilites into parents
		for (Node n : visited) {
			Node current = n;
			while (current.parent != null) {
				if (current.parent.probability != 0) {
					current.parent.probability += n.probability;
					n.probability = 0;
					break;
				}
				else {
					current = current.parent;
				}
			}
		}
		
		Set<Node> cuts = findMDL(wordnetNounTree, vb, marker);
		System.err.println("the size of the cust " + cuts.size());
		System.err.println(cuts.contains(wordnetNounTree));
		//System.err.println(cuts.iterator().next().probability);
		for (Node n : cuts) {
			System.out.println(n.probability + "  =" + n.synset.getDefinition());
		}
		
		
		for (Node n : cuts) {
			if (isChildNoun(nn, (NounSynset) n.synset)) {
				return n.probability / n.numNouns;
			}
		}
		System.err.println("The word was not found");
		System.err.println("the size of the array " + cuts.size());
		System.err.println(cuts.contains(wordnetNounTree));
		System.err.println(cuts.iterator().next().probability);
		return 0.0;
	}
	
	private void resetProbablities() throws InterruptedException {
		Queue<Node> squeue = new LinkedBlockingQueue<>();
		squeue.add(wordnetNounTree);
		while (!squeue.isEmpty()) {
			Node temp = squeue.remove();
			temp.probability = 0;
			for (Node s : temp.children) {
				squeue.add(s);
			}
		}
		
	}
	
	/*
	for (String mapping : counts.keySet()) {
		
	}
	
	
	
	//Map<String, Triple<List<Set<String>>, List<Set<String>>, int[][]>> out = new HashMap<>();
	for (String mapping : counts.keySet()) {
		/*List<Set<String>> govSet = probs.get(mapping + "_g").keySet().stream().map(s -> {
			Set<String> set = new HashSet<>();
			set.add(s);
			return set;
		}).collect(Collectors.toList());
		List<Set<String>> depSet = probs.get(mapping + "_d").keySet().stream().map(s -> {
			Set<String> set = new HashSet<>();
			set.add(s);
			return set;
		}).collect(Collectors.toList());
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
	}*/
	
	private Set<Node> findMDL(Node start, String vb, String marker) {
		if (start.children.length == 0) {
			Set<Node> out = new HashSet<>();
			out.add(start);
			return out;
		}
		else {
			Set<Node> root = new HashSet<>();
			root.add(start);
			Set<Node> c = new HashSet<>();
			for (Node ci : start.children) {
				c.addAll(findMDL(ci, vb, marker));
			}
			// Setting root to have probability temprorarily for ldash function
			double startprob = start.probability;
			for (Node ci : c) {
				start.probability += ci.probability;
			}
			//System.out.println("This cluster probability =" + start.probability);
			//System.out.println("num clusters below = " + start.numNodes);
			//System.out.println("ldash root =" + ldash(root,vb,marker) + "   ldash children =" + ldash(c, vb, marker));
			if (ldash(root, vb, marker) < ldash(c, vb, marker)) {
				// Assigning the probability of the subpartition.
				return root;
			}
			else {
				start.probability = startprob;
				return c;
			}
		}
	}
	
	// TODO stub method, figure out the math
	private double ldash(Set<Node> root, String vb, String marker) {
		// TODO deal with the null pointer on unseen thing
		//System.err.println(probs.get(marker + "_g").get(generateKey(vb, "VB")) * counts.get(marker));
		double t1 = ((root.size() - 1) / 2.0) * Math.log(probs.get(marker + "_g").get(generateKey(vb, "VB")) * counts.get(marker))/Math.log(2);
		double t2 = 0;
		for (Node n : root) {
			if (n.probability!=0) {
				t2 = t2 - n.probability*(probs.get(marker + "_g").get(generateKey(vb, "VB")) * counts.get(marker))
						*Math.log(n.probability/n.numNodes);//Math.log(2);
			}
		}
		//System.out.println("t2 =" + t2);
		
		
		return t1 + t2;
	}
	
	public boolean isChildNoun(String noun, NounSynset root) throws InterruptedException {
		Queue<NounSynset> squeue = new LinkedBlockingQueue<>();
		squeue.add(root);
		while (!squeue.isEmpty()) {
			NounSynset temp = squeue.remove();
			for(String s : temp.getWordForms()) {
				if (noun.contains(s)) {
					return true;
				}
			}
			for (NounSynset subsynset : temp.getHyponyms()) {
				squeue.add(subsynset);
			}
		}
		return false;
	}
	
	// first = number of nodes below this one
	public Pair<Integer, Integer> evaluateNode(Pair<Pair<NounSynset, NounSynset>, Double> input) throws InterruptedException {
		Queue<NounSynset> squeue = new LinkedBlockingQueue<>();
		int totalNodes = 0;
		squeue.add(input.getX().getY());
		while (!squeue.isEmpty()) {
			NounSynset temp = squeue.remove();
			totalNodes++;
			for (NounSynset s : temp.getHyponyms()) {
				squeue.add(s);
			}
		}
		return null;
	}
	
	/*
	public static Set<Synset> findRootNoun(NounSynset start) throws InterruptedException {
		Queue<NounSynset> squeue = new Queue<>();
		Set<Synset> out = new HashSet<>();
		squeue.add(start);
		while (!squeue.isEmpty()) {
			NounSynset tmp = squeue.remove();
			NounSynset[] hyp = tmp.getHypernyms();
			if (hyp.length ==0) {
				out.add(tmp);
			} else {
				for (NounSynset s : hyp) {squeue.add(s);}
			}
		}
		return out;
		
	}
	*/
	public WordnetModel(boolean requireSynchronized) throws InterruptedException {
		super(requireSynchronized);
		this.initilzieWordnet();
	}
	
	public WordnetModel(Path root, boolean requireSynchronized) throws IOException, InterruptedException {
		super(root, requireSynchronized);
		this.initilzieWordnet();
	}
	
	@Override
	protected void smooth() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public Double probabilityForBigram(TypedDependency td) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Double getHighestProbabilityForBigram(String first, String firstPos, String second, String secondPos) {
		try {
			Double p1 = getGeneralization("direct object",first,second);
			Double p2 = getGeneralization("direct object",second,first);
			Double p3 = getGeneralization("nominal subject",first,second);
			Double p4 = getGeneralization("nominal subject",second,first);
			return Math.max(p1, Math.max(p2, Math.max(p3, p4)));
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0.0;
	}
	
	
}

class Node {
	Node parent;
	Synset synset;
	Node[] children;
	double probability;
	int numNouns;
	int numNodes;
	
	public Node(Synset root) {
		this.synset = root;
		this.parent = null;
		this.numNodes = 1;
		this.numNouns = synset.getWordForms().length;
	}
	
	public Node(Node parent, Synset set) {
		this.synset = set;
		this.parent = parent;
		this.numNodes = 1;
		this.numNouns = synset.getWordForms().length;
	}
}
