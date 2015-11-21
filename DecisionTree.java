package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.controllers.examples.Legacy;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.DM;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.util.Random;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumMap;

public class DecisionTree extends Controller<MOVE>{

    private MOVE[] allMoves = MOVE.values();
    private Legacy lg = new Legacy();
    private int C = 6;
    private Random rnd=new Random();
    private Node root;

    public enum Attribute {MAZE,JUNCTION,PILL,POWER,HUNT,FLEE};
    public class Individual{
	public int mazeIndex, junction;
	public MOVE m, pillMove, powerPillMove, huntMove, fleeMove;
	    
	public Individual(){

	}
	public Individual(int mazeIndex, int junction, MOVE pillMove,
			  MOVE powerPillMove, MOVE huntMove, MOVE fleeMove,
			  MOVE m){
	    this.mazeIndex = mazeIndex;
	    this.junction = junction;
	    this.pillMove = pillMove;
	    this.powerPillMove = powerPillMove;
	    this.huntMove = huntMove;
	    this.fleeMove = fleeMove;
	    this.m = m;
	}
    };

    public class Node{
	public Attribute a;
	public MOVE decision;
	public ArrayList<Node> children;

	public Node(){}
	public Node(Attribute a, MOVE decision){
	    this.a = a;
	    this.decision = decision;
	    children = new ArrayList<Node>();
	}
    };
    
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

    private MOVE furtherGhost(Game state, int pnode){
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

    private void readTraining(HashSet<Individual> training){
	//Read in pre-processed training data
	try{
	    BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream("trainingData")));
	    String input=br.readLine();

	    while(input!=null){

		if (!input.equals("")) {
		    //System.out.println(input);
		    String[] values=input.split(",");
		    int index = 0;
		    int mazeIndex = Integer.parseInt(values[index++]);
		    int junction = Integer.parseInt(values[index++]);

		    MOVE pillMove = MOVE.valueOf(values[index++]);
		    MOVE powerPillMove = MOVE.valueOf(values[index++]);
		    MOVE huntMove = MOVE.valueOf(values[index++]);
		    MOVE fleeMove = MOVE.valueOf(values[index++]);
		    MOVE m = MOVE.valueOf(values[index++]);
		    training.add(new Individual(mazeIndex,junction,pillMove,
						powerPillMove,huntMove,fleeMove,m));
		}
		input=br.readLine();
	    }
	}
	catch(IOException ioe){}
    }

    private void writeTraining(HashSet<Individual> training){
	try{
	    FileOutputStream outS=new FileOutputStream("trainingData",true);
	    PrintWriter pw=new PrintWriter(outS);

	    for(int i = 0; i < training.size(); i++){
		Individual ind = training.get(i);
		StringBuilder sb=new StringBuilder();
		sb.append(ind.mazeIndex+","+ind.junction+","+ind.pillMove+","+
			  ind.powerPillMove+","+ind.huntMove+","+ind.fleeMove+","+
			  ind.m);
		pw.println(sb.toString());
		pw.flush();
	    }

	    outS.close();
	}
	catch(IOException ioe){}
    }

    private int binSearch(int[] junctions, int j){
	//guaranteed to have j in junctions
	int left = 0; int right = junctions.length-1;
	int mid = (right+left)/2;
	while(junctions[mid]!=j){
	    if (left+1==right) { mid = right; break; }
	    if (junctions[mid]>j) { right = mid; }
	    else { left = mid; }
	    mid = (right+left)/2;
	}
	return mid;
    }

    private void readRecordings(HashSet<Individual> training){
	//Convert gameplay recordings to usable training format
	//snippet obtained from http://stackoverflow.com/questions/4917326/how-to-iterate-over-the-files-of-a-certain-directory-in-java
	File dir = new File("recordings");
	File[] directoryListing = dir.listFiles();
	Game state=new Game(rnd.nextLong());

	for (File child : directoryListing) {
	    try{
		BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(child.getName())));
		String input=br.readLine();
		state.setGameState(input);
		int[] junctions = state.getJunctionIndices();

		Individual ind = new Individual();
		boolean flag = false;
		System.out.println("About to read file "+child.getName());
		while(input!=null){
		    if (!input.equals("")) {

			//System.out.println(input);
			String[] values=input.split(",");
			int mazeIndex = Integer.parseInt(values[0]);
			state.setGameState(input);
			int pnode = state.getPacmanCurrentNodeIndex();
			if (!state.isJunction(pnode)){
			    if (flag) {
				//System.out.println(pnode);
				ind.m = state.getPacmanLastMoveMade();
				training.add(ind);
				flag = false;
			    }
			    //input=br.readLine();
			    //continue;
			}

			else {
			    int j = binSearch(junctions,pnode);
			    MOVE pillMove = closer2Pill(state,pnode);
			    MOVE powerPillMove = closer2Power(state,pnode);
			    MOVE huntMove = closer2Ghost(state,pnode);
			    MOVE fleeMove = furtherGhost(state,pnode);
			    ind = new Individual(mazeIndex, j, pillMove,
						 powerPillMove, huntMove,
						 fleeMove, MOVE.NEUTRAL);
			    flag = true;
			}
		    }
		    input=br.readLine();
		}
	    }

	    catch(IOException ioe){}
	}
	
    }

    public DecisionTree(){
	super();
	System.out.println("Constructor");

	HashSet<Individual> training = new HashSet<Individual>();

	File f = new File("trainingData");
	if (f.exists()){
	    System.out.println("Reading in training data...");
	    readTraining(training);
	    System.out.println(training.size());
	}

	else{
	    System.out.println("Reading in raw gameplay data...");
	    readRecordings(training);
	    System.out.println(training.size());
	    System.out.println("Writing out training data...");
	    writeTraining(training);
	}

	System.out.println("Making tree...");
	makeTree(training);
    }

    private void makeTree(HashSet<Individual> training){
	HashSet<Integer> attributes = new HashSet<Integer>();
	for (Attribute a: Attribute.values()){
	    attributes.add(a);
	}
	makeTree(this.root, training, attributes, MOVE.NEUTRAL);
    }

    private void makeTree(Node n, HashSet<Individual> examples,
			  HashSet<Integer> attributes, MOVE parentPlurality){
	//if nothing left
	if (examples.size() == 0) { return parentPlurality; }
	//if everything is the same
	
	//if no attributes left
    }

    public MOVE getMove(Game game, long timeDue) {
	//Use the tree
	return MOVE.NEUTRAL;
    }
}
