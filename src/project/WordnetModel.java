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
	
	// main method that contains test code that was used to test the model and wordnet 
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
			System.out.println("eat: " + m.getGeneralization("direct object", "food", "eat"));
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
	
	/*
	 * Method that creates a tree based on the nouns synsets of wordnet.
	 * 
	 * Creates the tree by starting at the constant "entity" synset in the wordnet hierarchy and navigates 
	 * WordNet using a breadth first search algorithm
	 * 
	 * Will create unique nodes for branching paths through the tree, resutling in multiple nodes 
	 * corresponding to a single synset
	 * 
	 * Addionally will create a map that associates a particular synset (which turns out to be mappable) with 
	 * all of the nodes that own it. 
	 */
	public void initilzieWordnet() {
		wordnetNounTree = new Node(wordnetDB.getSynsets("entity", SynsetType.NOUN)[0]);
		NounTreeMap = new HashMap<>();
		List<Node> root = new LinkedList<>();
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
					List<Node> newList = new LinkedList<>();
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
	
	/*
	 * Primary method for returning statistical results over the wordnet tree. 
	 * 
	 * Takes in a verb and a target noun for gernalization and will perform MDL cut algorithm upon the data to generate 
	 * a set of "cuts" represetning the minimum description length of the file assuming that every cut has an even 
	 * probablity and that the probability of a given cut is distrubuted over the entire graph. 
	 * 
	 * Returns a double in the range [0.0-1.0] that corresponds to the predicted likelyhood of that particular verb-noun
	 * occuring in the specified marker slot in the network. 
	 */
	public double getGeneralization(String marker, String nn, String vb) {
		// Filtering out verbs that don't occur in the dataset
		if (probs.get(marker + "_g").get(generateKey(vb, "VB")) == null) {
			return 0.0;
		}
		convertToProbs();
		resetProbablities(wordnetNounTree);
		
		// Counting all the instances of nouns in the tree
		List<Pair<String, Double>> usedIDs = new ArrayList<>();
		double sum = 0;
		String gov = generateKey(vb, "VB");
		for (Entry<String, Double> e : probs.get(marker).entrySet()) {
			if (e.getKey().startsWith(gov)) {
				//**//System.out.println(e.getKey());
				//**//System.out.print("  " + splitKey(e.getKey()).group(5));
				Synset[] synsets = wordnetDB.getSynsets(splitKey(e.getKey()).group(5), SynsetType.NOUN);
				boolean words = false;
				for (Synset s : synsets) {
					if (NounTreeMap.get(s.getDefinition()) != null) {
						words = true;
					}
				}
				if (words) {
					sum += e.getValue(); /// counts.get(marker)
					usedIDs.add(new Pair<>(e.getKey().substring(e.getKey().indexOf('~') + 2), e.getValue()));
				}
				
			}
		}
		Double totalprob = 0.0;
		
		// Mapping those counts to the tree
		List<Node> visited = new LinkedList<>();
		for (Pair<String, Double> ID : usedIDs) {
			//**//System.out.println(ID.getX());
			Double prob = ID.getY() / sum;
			Synset[] synsets = wordnetDB.getSynsets(ID.getX().substring(0, ID.getX().indexOf(" :: ")), SynsetType.NOUN);
			int totalinstances = 0;
			for (Synset s : synsets) {
				List<Node> tmp = NounTreeMap.get(s.getDefinition());
				if (tmp != null) {
					totalinstances = totalinstances + tmp.size();
				}
				else {
					//System.out.println(ID.getX());
				}
				
			}
			
			for (Synset s : synsets) {
				List<Node> tmp = NounTreeMap.get(s.getDefinition());
				if (tmp != null) {
					for (Node n : tmp) {
						totalprob += (prob / totalinstances);
						n.probability = n.probability + (prob / totalinstances);
						visited.add(n);
					}
				}
				
			}
			
		}

		Set<Node> populated = getProbablilityClusters(wordnetNounTree);
		// Collapsing probabilites into parents based on the methods specified in our paper 
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
		populated = getProbablilityClusters(wordnetNounTree);
		//System.out.println("After collapsing there are " + populated.size() + " nodes in the tree");
		
		Set<Node> cuts = findMDL(wordnetNounTree, vb, marker);
		//System.out.println("the size of the cuts " + cuts.size());
		//System.out.println(cuts.contains(wordnetNounTree));
		//System.out.println("^was " + vb + "\n\n");
		//System.err.println(cuts.iterator().next().probability);
		
		populated = getProbablilityClusters(wordnetNounTree);
		//System.out.println("There are " + populated.size() + " nodes in the tree");
		
		cuts.addAll(populated);
		
		// Finds all locations in the tree the given noun appears, then checks the the cut set for every 
		// synset that the target noun appears and adds the probability of that synset to the total 
		// probability
		double prob = 0.0;
		
		for (Synset s : wordnetDB.getSynsets(nn, SynsetType.NOUN)) {
			List<Node> locations = NounTreeMap.get(s.getDefinition());
			if (locations != null) {
				for (Node target : locations) {
					for (Node n : cuts) {
						if (isChildNode(target, n)) {
							prob += n.probability / n.numNouns;
						}
					}
				}
			}
			
		}
		return prob;
	}
	
	/*
	 * Method that resets the probability of every node in the tree to 0 using a depth first search algorithm
	 */
	private void resetProbablities(Node n) {
		Queue<Node> squeue = new LinkedBlockingQueue<>();
		squeue.add(n);
		while (!squeue.isEmpty()) {
			Node temp = squeue.remove();
			temp.probability = 0;
			for (Node s : temp.children) {
				squeue.add(s);
			}
		}
		
	}
	
	/*
	 * MDL method specified in the Li and Abe 1998 paper. 
	 * 
	 * Caluclates the MDL of the given subtree and compares the MDL of the root node were all of the 
	 * subprobability collapsed into it, with the probabilty of the MDL of each subtree and chooses 
	 * which cut will be optimal. 
	 */
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
				for (Node cii : findMDL(ci, vb, marker)) {
					c.add(cii);
				}
			}
			// Setting root to have probability temprorarily for ldash function
			double startprob = start.probability;
			for (Node n : this.getProbablilityClusters(start)) {
				start.probability += n.probability;
				//n.probability = 0;
			}

			if (ldash(root, vb, marker) < ldash(c, vb, marker)) {
				// Assigning the probability of the subpartition.
				for (Node n : this.getProbablilityClusters(start)) {
					//start.probability+= n.probability;
					n.probability = 0;
				}
				return root;
			}
			else {
				start.probability = startprob;
				return c;
			}
		}
	}
	
	/*
	 * Method that calculates the MDL of a given set of nodes according to the algorithm specified in Li and Abe 1998
	 */
	private double ldash(Set<Node> root, String vb, String marker) {
		double t1 = ((root.size() - 1) / 2.0) * Math.log(probs.get(marker + "_g").get(generateKey(vb, "VB")) * counts.get(marker)) / Math.log(2);
		double t2 = 0;
		for (Node n : root) {
			if (n.probability != 0) {
				t2 = t2 - n.probability * (probs.get(marker + "_g").get(generateKey(vb, "VB")) * counts.get(marker)) * Math.log(n.probability / n.numNodes) / Math.log(2);
			}
		}
		return t1 + t2;
	}
	
	/*
	 * Helper method that uses bredth-first-search to poll the tree for whether it contains a given node
	 * 
	 * Takes node target and returns true if it is in the subtree described by the node scope
	 */
	private boolean isChildNode(Node target, Node scope) {
		Queue<Node> squeue = new LinkedBlockingQueue<>();
		squeue.add(scope);
		while (!squeue.isEmpty()) {
			Node temp = squeue.remove();
			if (target.equals(temp)) {
				return true;
			}
			for (Node child : temp.children) {
				squeue.add(child);
			}
		}
		return false;
	}
	
	/*
	 * Various constructors
	 */
	public WordnetModel(boolean requireSynchronized) {
		super(requireSynchronized);
		this.initilzieWordnet();
	}
	
	public WordnetModel(Path root, boolean requireSynchronized) throws IOException {
		super(root, requireSynchronized);
		this.initilzieWordnet();
	}
	
	@Override
	protected void doSmoothing() {/* Not used */}
	
	@Override
	public Double getHighestProbabilityForBigram(String first, String firstPos, String second, String secondPos) {
		Double p1 = getGeneralization("direct object", second, first);
		//Double p2 = getGeneralization("direct object",second,first);
		Double p3 = getGeneralization("nominal subject", second, first);
		//Double p4 = getGeneralization("nominal subject",second,first);
		return Math.max(p1, p3);
	}
	
	/*
	 * Method that returns a set of every node in the subtree that has a non-zero probability
	 */
	private Set<Node> getProbablilityClusters(Node root) {
		Set<Node> out = new HashSet<>();
		Queue<Node> squeue = new LinkedBlockingQueue<>();
		squeue.add(root);
		while (!squeue.isEmpty()) {
			Node tmp = squeue.poll();
			if (tmp.probability != 0) {
				out.add(tmp);
			}
			for (Node n : tmp.children) {
				squeue.add(n);
			}
		}
		return out;
	}
	
}

/*
 * Helper class that encapsulates information about a single node of the tree
 */
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
