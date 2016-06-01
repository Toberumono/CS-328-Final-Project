I. Building the Project
	1. Download the following libraries and place them in the project's root directory
		a. Apache Commons Math
		b. MIT's JWI
		c. SLF4J (the -api and -simple .jars)
		d. JAWS
		e. Stanford's CoreNLP library (Extract the .zip from their big download link)
	2. Run: ant

II. Running the Project
	1. Preparation
		a. Install WordNet (these instructions will assume that you have installed WordNet 3.1 through Homebrew)
		b. Download the BNC corpus
	2. Building your RawCountsModel
		a. Run: java -Dwordnet.database.dir="/usr/local/Cellar/wordnet/3.1/dict" -cp SelectionalPreference.jar project.CorpusParser "<bnc_root>/Texts/" "<path in which to store the RawCountsModel (must be an empty directory)>"
			i.   This will seem like it is hanging - give it some time (5+ hours)
			ii.  If this fails, remove all of the directories after G from the Texts directory
			iii. To generate smaller models, add additional subdirectories after the "/Texts/"
	3. Building more complex models
		a. For the PMI Model, run: java -Dwordnet.database.dir="/usr/local/Cellar/wordnet/3.1/dict" -cp SelectionalPreference.jar project.PMIModel "<path to the RawCountsModel root directory>"  "<path in which to store the PMI Model (must be an empty directory)>"
		b. For the Unsupervised Clustering Model, run: java -Dwordnet.database.dir="/usr/local/Cellar/wordnet/3.1/dict" -cp SelectionalPreference.jar project.LiModel "<path to the RawCountsModel root directory>"  "<path in which to store the Unsupervised Clustering Model (must be an empty directory)>"
			i. This model will take over a week to generate and requires over 32 GB of RAM when run on the full BNC corpus.  Generating this Model is NOT advisable
		c. The WordNet-based Model does not have any cached data, and so does not need to be separately generated
	4. Running the SentenceEvaluator
		a. Make sure that you have built the PMI Model
		b. Run: java -Dwordnet.database.dir="/usr/local/Cellar/wordnet/3.1/dict" -cp SelectionalPreference.jar project.SentenceEvaluator "<path to the PMIModel root directory>"
	5. Running the ModelEvaluator
		a. Generate the model that you are interested by following the previous instructions
		b. Edit line 92 of ModelEvaluator such that it constructs a model of the correct type
		c. Run: ant
		d. Run: java -Dwordnet.database.dir="/usr/local/Cellar/wordnet/3.1/dict" -cp SelectionalPreference.jar project.ModelEvaluator "<path to the Model root directory>"

III. Files of Interest
	1. projects.Model
	2. projects.PMIModel
	3. projects.LiModel
	4. projects.WordnetModel
	5. projects.SentenceEvaluator
	6. projects.ModelEvaluator