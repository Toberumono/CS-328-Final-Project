package project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import toberumono.structures.tuples.Pair;
import toberumono.structures.tuples.Triple;

import edu.smu.tspell.wordnet.NounSynset;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import edu.stanford.nlp.trees.TypedDependency;

public class WordnetModel extends Model {
	private static WordNetDatabase wordnetDB = WordNetDatabase.getFileInstance();
	
	public static void main(String[] args) {
		NounSynset nounSynset; 
		NounSynset[] hyponyms; 
		NounSynset[] hypernyms; 

		WordNetDatabase database = WordNetDatabase.getFileInstance(); 
		Synset[] synsets = database.getSynsets("cat", SynsetType.NOUN); 
		for (int i = 0; i < synsets.length; i++) { 
		    nounSynset = (NounSynset)(synsets[i]); 
		    hyponyms = nounSynset.getHyponyms(); 
		    hypernyms = nounSynset.getHypernyms();
		    String[] syns = nounSynset.getWordForms();
		    for(String s : syns) {
		    	System.err.println("      " + s);
		    }
		    
		    
		    
		    System.err.println(nounSynset.getWordForms().toString() + 
		            ": " + nounSynset.getDefinition() + ") has " + hyponyms.length + " hyponyms"); 
		    for (int j = 0; j<hyponyms.length;j++) {
		    	System.err.println(hyponyms[j].getWordForms()[0]);
		    }
		    System.err.println(nounSynset.getWordForms()[0] + 
		            ": " + nounSynset.getDefinition() + ") has " + hypernyms.length + " hypernyms"); 
		    for (int j = 0; j<hypernyms.length;j++) {
		    	System.err.println(hypernyms[j].getWordForms()[0]);
		    }
		}
	}
	
	// For right now this is only for verbs, will generalize later
	public List<Pair<Pair<Synset,Synset>,Double>> getGeneralization(String mapping, String vb) {
		convertToProbs();
		List<Pair<Synset,Double>> out = new ArrayList<>();
		List<Pair<String,Double>> usedIDs = new ArrayList<>();
		double sum = 0;
		String gov = generateKey(vb, "VB");
		for (Entry<String, Double> e : probs.get(mapping).entrySet()) {
			if (e.getKey().startsWith(gov)) {
				sum += e.getValue() * counts.get(mapping);
				usedIDs.add(new Pair<>(e.getKey().substring(e.getKey().indexOf('~') + 2), e.getValue() * counts.get(mapping)));
			}
		}
		
		for (Pair<String,Double> ID : usedIDs) {
			Double prob = ID.getY()/sum;
			Synset[] synsets = wordnetDB.getSynsets(ID.getX().substring(0, ID.getX().indexOf(" :: ")));
			for (Synset s : synsets) {
				Synset[] hypernyms = ((NounSynset)s).getHypernyms();
				for(NounSynset p : hypernyms) {
					out.add(new Pair<>(new Pair<>(p, s),prob/synsets.length));
				}
			}
		}
		
		
		
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
			*/
			
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

	@Override
	public Double probabilityForBigram(TypedDependency td) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
}
