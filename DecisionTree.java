package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.controllers.examples.Legacy;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.DM;

import java.util.Random;
import java.util.ArrayList;
import java.util.EnumMap;
import java.io.File;

public class DecisionTree extends Controller<MOVE>{

    public class Individual{
	public int mapIndex, pnode;	    
	public MOVE m, pillMove, powerPillMove, huntMove, fleeMove;
	    
	public Individual(){
	    mapIndex = -1;
	}
	public Individual(int mapIndex, int pnode, MOVE pillMove,
			  MOVE powerPillMove, MOVE huntMove, MOVE fleeMove,
			  MOVE m){
	    this.mapIndex = mapIndex;
	    this.pnode = pnode;
	    this.pillMove = pillMove;
	    this.powerPillMove = powerPillMove;
	    this.huntMove = huntMove;
	    this.fleeMove = fleeMove;
	    this.m = m;
	}
    };


    private MOVE[] allMoves = MOVE.values();
    private Legacy lg = new Legacy();
    private int C = 6;

    private Random rnd=new Random();
    
    /*
      As in other controllers, determine a "turn sequence"

      If the current recorded state is not the last
        pair with next "lastMoveMade"
	Load into Game object
	if pacman is @ a junction, obtain & save the following
	  maze index
	  which junction index
	  the move that brings us closer to the closest pill
	  the move that brings us closer to the closest power pill
	  the move that brings us closer to the closest edible ghost
	  the move that brings us further from the closest inedible ghost

      If current testing state is not at a junction, keep on making non-rev move
      Else, go through decision tree and fetch move to make
     */

    private MOVE closer2Power(Game state, int pnode){
	MOVE ans = MOVE.NEUTRAL;

	int shortest2pill = Integer.MAX_VALUE;
	int pillIndex = 0;
	//Find distances to power pills
	for(int i: state.getActivePowerPillsIndices()){
	    int dist = state.getShortestPathDistance(pnode,i);
	    if (dist < shortest2pill) {
		shortest2pill = dist;
		pillIndex = i;		
	    }
	}

	if (shortest2pill != Integer.MAX_VALUE)
	    { ans = state.getNextMoveTowardsTarget(pnode,pillIndex,DM.PATH); }
	return ans;
    }

    private MOVE closer2Pill(Game state, int pnode){
	int shortest2pill = Integer.MAX_VALUE;
	int pillIndex = 0;
	//Find distances to pills
	for(int i: state.getActivePillsIndices()){
	    int dist = state.getShortestPathDistance(pnode,i);
	    if (dist < shortest2pill) {
		shortest2pill = dist;
		pillIndex = i;
	    }
	}
	return state.getNextMoveTowardsTarget(pnode,pillIndex,DM.PATH);
    }

    private MOVE closer2Ghost(Game state, int pnode){
	MOVE ans = MOVE.NEUTRAL;
	int shortest2edible = Integer.MAX_VALUE;
	int gnode = 0;
	for(GHOST g: GHOST.values()){
	    int i = state.getGhostCurrentNodeIndex(g);
	    int dist = state.getShortestPathDistance(pnode,i);
	    if (state.isGhostEdible(g) && dist < shortest2edible){
		shortest2edible = dist;
		gnode = i;
	    }
	}
	if (shortest2edible != Integer.MAX_VALUE){
	    ans = state.getNextMoveTowardsTarget(pnode,gnode,DM.PATH);
	}
	return ans;
    }

    private MOVE furtherGhost(Game state){
	MOVE ans = MOVE.NEUTRAL;
	int shortest2inedible = Integer.MAX_VALUE;
	int gnode = 0;
	for(GHOST g: GHOST.values()){
	    int i = state.getGhostCurrentNodeIndex(g);
	    int dist = state.getShortestPathDistance(pnode,i);
	    if (!state.isGhostEdible(g) && dist < shortest2inedible){
		shortest2inedible = dist;
		gnode = i;
	    }
	}
	if (shortest2inedible != Integer.MAX_VALUE){
	    ans = state.getNextMoveAwayFromTarget(pnode,gnode,DM.PATH);
	}
	return ans;
    }

    private readTraining(ArrayList<Individual> training){
	//Read in pre-processed training data
	BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream("trainingData")));
	String input=br.readLine();

	while(input!=null){

	    if (!input.equals("")) {
		String[] values=state.split(",");
		int index = 0;
		int mazeIndex = Integer.parseInt(values[index++]);
		int pnode = Integer.parseInt(values[index++]);
		MOVE pillMove = Integer.parseInt(values[index++]);
		MOVE powerPillMove = Integer.parseInt(values[index++]);
		MOVE huntMove = Integer.parseInt(values[index++]);
		MOVE fleeMove = Integer.parseInt(values[index++]);
		MOVE m = Integer.parseInt(values[index++]);
		training.add(new Individual(mazeIndex,pnode,pillMove,
					    powerPillMove,huntMove,fleeMove,m));
	    }
	    input=br.readLine();
	}
    }

    private writeTraining(ArrayList<Individual> training){

	for(int i = 0; i < training.size(); i++){
	    Individual ind = training.get(i);
	    StringBuilder sb=new StringBuilder();
	    sb.append(ind.mazeIndex+","+ind.pnode+","+ind.pillMove+","+
		      ind.powerPillMove+","+ind.huntMove+","+ind.fleeMove+","+
		      ind.m+"\n");
	}

	FileOutputStream outS=new FileOutputStream("trainingData",true);
	PrintWriter pw=new PrintWriter(outS);
	pw.println(data);
	pw.flush();
	outS.close();
    }

    private readRecordings(ArrayList<Individual> training){
	//Convert gameplay recordings to usable training format
	//snippet obtained from http://stackoverflow.com/questions/4917326/how-to-iterate-over-the-files-of-a-certain-directory-in-java
	File dir = new File("recordings");
	File[] directoryListing = dir.listFiles();
	Game state = new Game();

	for (File child : directoryListing) {
	    BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(child.getName())));
	    String input=br.readLine();
	    Individual ind = new Individual();
	    boolean flag = false;

	    while(input!=null){
		if(!input.equals("")) {
		    String[] values=state.split(",");
		    int mazeIndex = values[0];
		    state.setGameState(input);
		    int pnode = state.getPacmanCurrentNodeIndex();
		    if (!state.isJunction(pnode)){
			if (flag) {
			    ind.m = state.lastMoveMade;
			    training.add(ind);
			    flag = false;
			}
			continue;
		    }
			
		    MOVE pillMove = closer2Pill(state,pnode);
		    MOVE powerPillMove = closer2Power(state,pnode);
		    MOVE huntMove = closer2Ghost(state,pnode);
		    MOVE fleeMove = furtherGhost(state,pnode);
		    ind = new Individual(mazeIndex,pnode,pillMove,
					 powerPillMove,huntMove,fleeMove,
					 MOVE.NEUTRAL);
		    flag = true;
		}

		input=br.readLine();   
	    }
	}
    }

    public DecisionTree(){
	super();

	File f = new File("trainingData");
	ArrayList<Individual> training = new ArrayList<Individual>();

	if (f.exists()){ readTraining(training); }

	else{
	    readRecordings(training);
	    writeTraining(training);
	}

	makeTree(training);
    }

    private void makeTree(ArrayList<Individual> training){

    }

    public MOVE getMove(Game game, long timeDue) {

	return MOVE.NEUTRAL;
    }
}
//Foobar
