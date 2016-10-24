/**
 * 
 */
package saar;

/**
 * @author quispell
 *
 */
import sim.engine.*;
import sim.util.*;
import ec.util.*;
import sim.field.continuous.*;
import sim.field.network.*;
import saar.agents.*;
import saar.ui.*;
import com.beust.jcommander.*;

public class Saar extends SimState
{
	

///////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//Static constants
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private static final long serialVersionUID = 1L;
	
	// risk types
	public static final int NONE = 0;  // Dummy to allow for sending a sentiment vector as content[0] in Message
	public static final int FLOOD = 1;
	
	// auxiliary
	public static final int HIGHER = 0;
	public static final int EQUAL = 1;
	public static final int LOWER = 2;
		
///////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//Properties, constructors and main
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	// model properties
	private Continuous2D area ; 
	public ec.util.MersenneTwisterFast randomGenerator;
	private Network friends;
	private Census census;
	private Medium medium;
	
	// configuration properties
	private int numCitizens;
	private int primaryRiskType;
	private String networkType; 
	private String riskManagerBehavior;
	private String mediaBehavior;
	private String opinionDynamic;
	private DoubleBag objectiveRisks;
	private int eventMemory;
	private Double confidence;
	
	public Continuous2D getArea() { return area;} 
	public Network getFriends() { return friends;}
	public Census getCensus() { return census; }
	
	public int getNumCitizens() { return numCitizens;}
	public void setNumCitizens(int numCitizens) { this.numCitizens = numCitizens; }
	public int getPrimaryRiskType() { return primaryRiskType ; }
	public void setPrimaryRiskType( int newRiskType ) { primaryRiskType = newRiskType ; } 
	public String getNetworkType() { return networkType; }
	public void setNetworkType(String networkType) { this.networkType = networkType; }
	public String getRiskManagerBehavior() { return riskManagerBehavior; }
	public void setRiskManagerBehavior(String riskManagerBehavior) { this.riskManagerBehavior = riskManagerBehavior; }
	public String getMediaBehavior() { return mediaBehavior; }
	public void setMediaBehavior(String mediaBehavior) { this.mediaBehavior = mediaBehavior; }
	public DoubleBag getObjectiveRisks() { return objectiveRisks; }
	public void setObjectiveRisks(DoubleBag objectiveRisk) { this.objectiveRisks = objectiveRisk; }
	public int getEventMemory() { return eventMemory; }
	public void setEventMemory(int eventMemory) { this.eventMemory = eventMemory; }
	
	public Medium getMedium() { return medium;}
	
		
	/**
	 * 
	 * @param seed
	 * @param NetworkType
	 * @param ObjectiveFirstRisk
	 * @param NumCitizens
	 * @param EventMemory
	 */
	public Saar(long seed, String NetworkType, String OpinionDynamic, Double ObjectiveFirstRisk, int NumCitizens, int EventMemory, Double Confidence) {
		super(seed);
		area = new Continuous2D(1.0,100,100);
		randomGenerator = new MersenneTwisterFast();
		friends = new Network(false);
		objectiveRisks = new DoubleBag();
		
		networkType = NetworkType;
		opinionDynamic = OpinionDynamic;
		objectiveRisks.add(ObjectiveFirstRisk);
		numCitizens = NumCitizens;
		eventMemory = EventMemory;
		confidence = Confidence;
		
	}
	
	public Saar(long seed, CommandLineArgs config) {
		super(seed);
		area = new Continuous2D(1.0,100,100);
		randomGenerator = new MersenneTwisterFast();
		friends = new Network(false);
		objectiveRisks = new DoubleBag();
		
		networkType = config.networkType;
		opinionDynamic = config.opinionDynamic;
		objectiveRisks.add(config.objectiveRisk);
		numCitizens = 400;
		eventMemory = config.eventMemory;
		primaryRiskType = config.primaryRiskType;
		confidence = config.confidence;
	}
	
	/**
	 * @param args
	 */
	
	public static void main(String[] args) 
	{		
		// parse arguments. If no arguments are given, defaults from CommandLindArgs Class are used
		CommandLineArgs commandLineArgs = new CommandLineArgs();
		new JCommander(commandLineArgs,args);

		// create the model with command line arguments
		SimState state = new Saar(System.currentTimeMillis(),commandLineArgs);
		
		// start the model
		int numJobs = commandLineArgs.numJobs;
		int numSteps = commandLineArgs.numSteps;
		state.nameThread();
		for(int job = 0; job < numJobs; job++)
		{
			state.setJob(job);
			state.start();
			do
				if (!state.schedule.step(state)) break;
			while(state.schedule.getSteps() < numSteps);
			state.finish();
		}
		
		// Exit
		System.exit(0);

	}


///////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Initializing and Starting methods
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * 
	 */
	public void start()
	{
		super.start();
		
		// clear area
		area.clear();
		
		// add Census object for gathering statistics
		census = new Census(2,this.getObjectiveRisk(0));
		census.initializeLogFile();
		schedule.scheduleRepeating(census);
		
		// add citizens
		census.log("Creating agents: " + numCitizens);
		census.log("Using opinion dynamic: " + opinionDynamic );
		int agentType;
		switch ( opinionDynamic ) {
			case "DEGROOT":
				agentType = Citizen.DEGROOT;
				break;
			case "HEGSELMAN":
				agentType = Citizen.HEGSELMAN;
				break;
			case "ONGGO":
				agentType = Citizen.ONGGO;
				break;
			default:
				// when no opinion dynamic is given, use the first
				agentType = 0; 
				break;
		}
		int xPos = 8;
		int yPos = 2;
		Double initialRisk = 0.0;
		Double lowerRiskBound = objectiveRisks.get(0) * 0.95;
		Double riskInterval = objectiveRisks.get(0) * 0.1;
		for(int i = 0; i < numCitizens; i++)
		{
			initialRisk = lowerRiskBound + randomGenerator.nextDouble() * riskInterval; 
			Citizen citizen = new Citizen(i, this, agentType, initialRisk, confidence); 
			
			// spread citizens over the area
			if ( xPos < 86 ){ 
				xPos = xPos + 3;
			}
			else {
				if(yPos < 8) xPos = 11;
				else xPos = 2;
				yPos = yPos + 3;
			}
			area.setObjectLocation(citizen, new Double2D(xPos, yPos));	
			
			// add citizen to social network and schedule 
			friends.addNode(citizen);
			schedule.scheduleRepeating(citizen);
			
		}
		
		// create edges in social network
		switch ( networkType ) 
		{
			case "Lattice":
				createNetworkLattice();
				break;
			case "WattsBeta":
				createNetworkWattsStrogatz(8, 0.25);
				break;
			default:
				System.out.println("None !!!");
				System.out.println("*** Warning: no social network has been set up.");
				break;
				
		}		
		
		// add medium
		medium = new Medium(-1, this, Medium.OBJECTIVE);
		area.setObjectLocation(medium,new Double2D(2.5,3));
		schedule.scheduleRepeating(medium);
		
		// place census object visible in gui
		area.setObjectLocation(census, new Double2D(-2, 3));
		
	
		census.log("Event Memory: " + eventMemory + " ");
		census.log("Objective Risk: " + objectiveRisks.getValue(0) + " ");
	
	}
	
	/**
	 * 
	 */
	public void finish()
	{
		census.endSession();
		super.finish();
	}
	
	/**
	 * @param networkType
	 */
	
	public void createNetworkLattice()
	{
		census.log("Creating Social Network: Lattice");

		Bag citizens = new Bag(friends.getAllNodes()); // create copy to be sure the Bag doesn't change or gets garbage collected 

		for(int i = citizens.size() - 1 ; i > 0 ; i--)
		{
			Object citizen1 = citizens.get(i);
			Object citizen2 = citizens.get(i-1);
			friends.addEdge(citizen1, citizen2, 1.0);
		}
	}
	
	/**
	 * @param degree
	 * @param beta
	 */
	
	public void createNetworkWattsStrogatz(int degree, double beta) 
	{
		census.log("Creating Social Network: Watts beta");
		
		Bag citizens = new Bag(friends.getAllNodes()); // create copy to be sure the Bag doesn't change or gets garbage collected
		Bag neighbours = new Bag();
		
		for (int i = 0 ; i < citizens.size() ; i++  ) 
		{
			Object citizen = citizens.get(i);
			Double2D pos = area.getObjectLocation(citizen);
			
			// get degree neigbours
			neighbours = area.getNearestNeighbors(pos, degree, false, false, true, neighbours);
			
			// wire neighbours and/or random node		
			Object acquaintance = new Object();
			for ( int n = 0; n < degree ; n++ ) // should loop to citizens.size(), but that can be larger than degree
			{
				
				Object neighbour = neighbours.get(n);
				if ( random.nextDouble() < beta ) 
				{
					// wire with random node with probability beta
					// TODO: this is not entirely correct; a node has to be randomly selected from a set without already wired nodes and neighbour
					do {
						int tmp = randomGenerator.nextInt(numCitizens);
						acquaintance = citizens.get(tmp);
					}
					while ( citizen == acquaintance || neighbour == acquaintance || friends.getEdge(citizen, acquaintance) != null );
		
					
					friends.addEdge(citizen,acquaintance,1.0);
				} 
				else
				{
					// otherwise, just wire neighbour (if not wired already) 
					if ( friends.getEdge(citizen, neighbour) == null )
						friends.addEdge(citizen,neighbour,1.0);
				}
			}
		}

	}
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Auxiliary 
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	
	/**
	 * 
	 * @param ID
	 * @return
	 */
	
	public Object getAgent(int ID)
	{
		// TODO: for speed, maybe create a separate map to look up agents by ID

		Bag individuals = friends.getAllNodes();
		for ( int i = 1; i < individuals.size() ; i++ )
		{
			if ( ((Citizen) individuals.get(i)).getAgentID() == ID )
				return individuals.get(i);
		}
		return null;
	}
	
	/**
	 * 
	 * @param riskType
	 * @return
	 */
	public Double getObjectiveRisk(int riskType)
	{
		try {
			return objectiveRisks.get(riskType);
		}
		catch (Exception e)
		{
			// TODO: handle this better
			return 0.0;
		}
		
	}
	
}