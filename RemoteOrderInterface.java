package genBot2;

import java.io.FileNotFoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.Properties;

public interface RemoteOrderInterface extends Remote {

	public CocktailWithName[] getNamedPopulation(String evolutionStackName) throws RemoteException;
	
	public void setCocktailFitness(String evolutionStackName, String name, double fitnessInput) throws RemoteException, NotEnoughRatedCocktailsException, SQLException;
	
	public boolean canEvolve(String evolutionStackName) throws RemoteException;
	
	public void evolve(String evolutionStackName) throws RemoteException, SQLException, NotEnoughRatedCocktailsException;
	
	public void generateEvolutionStack(String evolutionStackName, String fitnessCheckName, String recombinationName, boolean dbReset, String propPath, double stdDeviation) throws RemoteException, SQLException;
	
	public void generateEvolutionStack(String evolutionStackName, Ingredient[] allowedIngredients, int populationSize, int truncation, int elitism, String dbDriverPath, boolean dbReset, String fitnessCheckName, String recombinationName, double stdDeviation, String propPath)  throws RemoteException, SQLException;
	
	public String[] listEvolutionStacks() throws RemoteException;
	
	public boolean containsEvolutionStack(String evolutionStackName) throws RemoteException;
	
	public Properties getProps(String evolutionStackName) throws RemoteException, FileNotFoundException;
	
	public void setProps(String evolutionStackName, int populationSize, int truncation, int elitism, double stdDeviation, String dbDriverPath, String booleanAllowedIngredientsString) throws RemoteException;
	
	public void queueCocktail(String evolutionStackName, String cocktailName) throws RemoteException;
}
