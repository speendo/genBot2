package genBot2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

/*
 * some kind of a container class - this class is responsible for generating a new
 * generation of cocktails. Apart from information stored in the CocktailGeneration
 * this class stores the generation number.
 */
public class EvolutionAlgorithmManager {
	
	private CocktailGenerationManager genManager;
	
	private int truncation;
	private int elitism;
	
	private int populationSize;
	private CheckFitness fitnessCheck;
	private Recombination recombination;
	private DataBaseDriver dbDriver;
	
	private String propPath;
	private String dbDriverPath;
	
	private boolean didJustLoad = false;
		
	/*
	 * constructor
	 * @param generationNumber the number of the generation
	 * @param generationSize how many Cocktails should be in the generation
	 * @param fitnessCheck a class that implements CheckFitness and performs a fitness check
	 */
	public EvolutionAlgorithmManager(int populationSize, int truncation, int elitism, String dbDriverPath, boolean dbReset, CheckFitness fitnessCheck, Recombination recombination, String propPath) throws SQLException {		
		Properties props = new Properties();
		props.setProperty("populationSize", String.valueOf(populationSize));
		props.setProperty("truncation", String.valueOf(truncation));
		props.setProperty("elitism", String.valueOf(elitism));
		props.setProperty("dbDriverPath", dbDriverPath);
		
		try {
			props.store(new FileOutputStream(new File(propPath + ".properties")), null);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		loadProps(propPath);
		
		constructRest(fitnessCheck, recombination, dbReset, propPath);
	}
	
	public EvolutionAlgorithmManager(CheckFitness fitnessCheck, Recombination recombination, boolean dbReset, String propPath) throws SQLException {
		loadProps(propPath);
		
		try {
			constructRest(fitnessCheck, recombination, dbReset, propPath);
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void loadProps(String propPath) {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(propPath + ".properties"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println(props.getProperty("populationSize"));
		updateProps(Integer.parseInt(props.getProperty("populationSize")), Integer.parseInt(props.getProperty("truncation")), Integer.parseInt(props.getProperty("elitism")), props.getProperty("dbDriverPath"));
	}
	
	private void updateProps(int populationSize, int truncation, int elitism, String dbDriverPath) {
		this.populationSize = populationSize;
		this.truncation = truncation;
		this.elitism = elitism;
		this.dbDriverPath = dbDriverPath;
	}

	private void constructRest(CheckFitness fitnessCheck, Recombination recombination, boolean dbReset, String propPath) throws SQLException {
		this.fitnessCheck = fitnessCheck;
		this.recombination = recombination;
		
		this.propPath = propPath;
		
		this.dbDriver = new DataBaseDriver(dbDriverPath, dbReset);
		
		if (dbDriver.getLastGenerationNumber() == 0) {
			genManager = new CocktailGenerationManager(populationSize);
		} else {
			genManager = load();
			didJustLoad = true;
		}

	}
		
	/*
	 * Evolves a cocktail generation with a specified stdDeviation and elitism. First
	 * Crossover is applied, next mutation and then elitism
	 * @param stdDeviation standard deviation
	 * @param elitism number of cocktails to come to enter the next generation
	 * @return the new cocktail generation
	 */
	public void evolve() throws FitnessNotSetException, SQLException {
		if (didJustLoad) {
			didJustLoad = false;
		} else {
			save();			
		}
		
		// load poperties - they may have been updated
		loadProps(propPath);
		
		genManager.increaseGenerationNumber();
		
		// Clone current generation
		// CocktailGeneration nextGeneration = cocktailGeneration.clone();
		
		// Truncation
		CocktailGeneration nextGeneration = truncation(truncation, genManager.getCocktailGeneration());

		// Crossover & Mutation
		nextGeneration = recombination.recombine(nextGeneration, populationSize);

		// Elitism
		nextGeneration = applyElitism(elitism, genManager.getCocktailGeneration(), nextGeneration);
		
		genManager.setGeneration(nextGeneration);
	}
	
	public CocktailGenerationManager load() throws SQLException {
		return load(dbDriver.getLastGenerationNumber());
	}
	
	public CocktailGenerationManager load(int number) throws SQLException {
		return dbDriver.select(number);
	}
	
	public void loadFromDB(int number) throws SQLException {
		genManager = load(number);
		
		didJustLoad = true;
	}
	
	public void loadLastFromDB() throws SQLException {
		genManager = load();
		
		didJustLoad = true;
	}
	
	public void save() throws SQLException {
		doSave(genManager.getGenerationNumber(), genManager);
	}
	
	public void doSave(int generationNumber, CocktailGenerationManager cocktailGenerationManager) throws SQLException {
		dbDriver.insert(generationNumber, cocktailGenerationManager);
	}
	
	/*
	 * checks the fitness for the whole cocktail generation
	 */
	public void evaluate() {
		if (genManager.getCocktailGeneration().hasNextRandomCocktail()) {
			genManager.getCocktailGeneration().getNextRandomCocktail().setFitness(fitnessCheck);
		}
	}

	/*
	 * applies truncation to the generation - the worst cocktails are removed from the
	 * generation
	 * @param truncation how many cocktails should be removed from the generation
	 * @param cocktailGeneration 
	 */
	public CocktailGeneration truncation(int truncation, CocktailGeneration cocktailGeneration) {
		if (truncation < 0) {
			throw new IllegalArgumentException("Invalid number of truncated cocktails (" + truncation + ")!");
		} else if (truncation >= cocktailGeneration.getPopulationSize()) {
			throw new IllegalArgumentException("You try to truncate all cocktails of the generation. This is impossible");
		}
		Cocktail[] rankedCocktails = cocktailGeneration.rankCocktails();
		
		Cocktail[] truncatedCocktails = new Cocktail[rankedCocktails.length - truncation];
		
		for (int i = 0; i < rankedCocktails.length - truncation; i++) {
			truncatedCocktails[i] = rankedCocktails[i];
		}
		
		return new CocktailGeneration(truncatedCocktails);
	}

	/*
	 * applies elitism to the generation - some random cocktails are replaced with the
	 * best cocktails from the previous generation
	 * @param elitism number of cocktails to be replaced
	 * @param oldCocktailGeneration the previous cocktail generation
	 * @param newCocktailGeneration the new cocktail generation
	 * @return a cocktail generation with elitism applied
	 */
	public CocktailGeneration applyElitism(int elitism, CocktailGeneration oldCocktailGeneration, CocktailGeneration newCocktailGeneration) {
		if (elitism < 0) {
			throw new IllegalArgumentException("Invalid number of elite-Cocktails (" + elitism + ")!");
		}

		
		if (elitism > newCocktailGeneration.getPopulationSize()) {
			elitism = newCocktailGeneration.getPopulationSize();
		}
		if (elitism > oldCocktailGeneration.getPopulationSize()) {
			elitism = oldCocktailGeneration.getPopulationSize();
		}
		
		// rank
		Cocktail[] oldCocktails = oldCocktailGeneration.rankCocktails();
		
		Cocktail[] newPopulation = newCocktailGeneration.getPopulation();
		int[] randomOrder = newCocktailGeneration.generateRandomPopulationOrder();
		
		// now we have the previous cocktails ranked. Now replace <elitism> cocktails in the current population
		for (int i = 0; i < elitism; i++) {
			newPopulation[randomOrder[i]] = oldCocktails[i].copy();
		}
		
		return new CocktailGeneration(newPopulation);
	}
	
	public CocktailGenerationManager getGenManager() {
		return genManager;
	}
}
