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
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Iterator;
import java.lang.Math;

public class DecisionTree extends Controller<MOVE>{

    private MOVE[] allMoves = MOVE.values();
    private Legacy lg = new Legacy();
    private int C = 6;
    private Random rnd=new Random();
    private Node root;

    public enum Attribute {PILL,POWER,HUNT,FLEE};
    public class Individual{
	public MOVE m, pillMove, powerPillMove, huntMove, fleeMove;	    
	public Individual(){}
	public Individual(MOVE pillMove, MOVE powerPillMove,
			  MOVE huntMove, MOVE fleeMove, MOVE m){
	    this.pillMove = pillMove;
	    this.powerPillMove = powerPillMove;
	    this.huntMove = huntMove;
	    this.fleeMove = fleeMove;
	    this.m = m;
	}
    };

    public class Node{
	public MOVE decision;
	public boolean leaf;
	public Attribute a;
	public ArrayList<Node> children;

	public Node(){}
	public Node(Attribute a){
	    this.a = a;	 
	    this.children = new ArrayList<Node>();
	    this.leaf = false;
	}
	public Node(MOVE decision){
	    this.decision = decision;
	    this.leaf = true;
	}
    };
    
    /*
      As in other controllers, determine a "turn sequence"

      If the current recorded state is not the last
        pair with next "lastMoveMade"
	Load into Game object
	if pacman is @ a junction, obtain & save the following
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

		    MOVE pillMove = MOVE.valueOf(values[index++]);
		    MOVE powerPillMove = MOVE.valueOf(values[index++]);
		    MOVE huntMove = MOVE.valueOf(values[index++]);
		    MOVE fleeMove = MOVE.valueOf(values[index++]);
		    MOVE m = MOVE.valueOf(values[index++]);
		    training.add(new Individual(pillMove,powerPillMove,
						huntMove,fleeMove,m));
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

	    for(Individual ind: training){
		//Individual ind = training.get(i);
		StringBuilder sb=new StringBuilder();
		sb.append(ind.pillMove+","+ind.powerPillMove+","+
			  ind.huntMove+","+ind.fleeMove+","+ind.m);
		pw.println(sb.toString());
		pw.flush();
	    }

	    outS.close();
	}
	catch(IOException ioe){}
    }

    private void readRecordings(HashSet<Individual> training){
	//Convert gameplay recordings to usable training format
	//snippet obtained from http://stackoverflow.com/questions/4917326/how-to-iterate-over-the-files-of-a-certain-directory-in-java
	File dir = new File("recordings");
	File[] directoryListing = dir.listFiles();
	Game state=new Game(rnd.nextLong());

	for (File child : directoryListing) {
	    try{
		//System.out.println("About to read file "+child.getName());

		BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream("recordings/"+child.getName())));
		//System.out.println("Made br");

		String input=br.readLine();
		//System.out.println("Read first line");

		state.setGameState(input);
		//System.out.println("Loaded state");

		Individual ind = new Individual();
		boolean flag = false;

		while(input!=null){
		    if (!input.equals("")) {

			//System.out.println(input);
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
			    MOVE pillMove = closer2Pill(state,pnode);
			    MOVE powerPillMove = closer2Power(state,pnode);
			    MOVE huntMove = closer2Ghost(state,pnode);
			    MOVE fleeMove = furtherGhost(state,pnode);
			    ind = new Individual(pillMove,powerPillMove,
						 huntMove,fleeMove,
						 MOVE.NEUTRAL);
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

	HashSet<Individual> training = new HashSet<Individual>();

	File f = new File("trainingData");
	if (f.exists()){
	    //System.out.println("Reading in training data...");
	    readTraining(training);
	    //System.out.println(training.size());
	}

	else{
	    //System.out.println("Reading in raw gameplay data...");
	    readRecordings(training);
	    //System.out.println(training.size());
	    System.out.println("Writing out training data...");
	    writeTraining(training);
	}

	//System.out.println("Making tree...");
	makeTree(training);
    }

    private void makeTree(HashSet<Individual> training){
	HashSet<Attribute> attributes = new HashSet<Attribute>();
	for (Attribute a: Attribute.values()){
	    attributes.add(a);
	}
	this.root = makeTree(training, attributes, MOVE.NEUTRAL);
    }

    private float info(HashSet<Individual> examples){
	if (examples.size() == 0) { return 0; }
	float ans = 0;
	HashMap<MOVE,HashSet<Individual> > move2subset = new HashMap<MOVE,HashSet<Individual> >();
	partition(examples,Attribute.PILL,move2subset,true,false);
	for(MOVE m: allMoves){
	    if (move2subset.get(m) == null) { continue; }
	    float p = (float)move2subset.get(m).size() / examples.size();
	    ans += p * Math.log(p) / Math.log(2);
	}
	return -ans;
    }

    private Attribute bestAttribute(HashSet<Individual> examples,
				    HashSet<Attribute> attributes){
	Attribute ans = Attribute.FLEE;
	float maxGain = 0;

	for (Attribute a: Attribute.values()){
	    float info_a = 0;
	    HashMap<MOVE,HashSet<Individual> > move2subset = new HashMap<MOVE,HashSet<Individual> >();
	    partition(examples, a, move2subset, false, false);
	    for (MOVE m: allMoves){
		if (move2subset.get(m) == null) { continue; }
		HashSet<Individual> subset = move2subset.get(m);
		info_a += info(subset) * subset.size() / examples.size();
	    }

	    float gain = info(examples) - info_a;

	    if (gain > maxGain){
		maxGain = gain;
		ans = a;
	    }
	}

	return ans;
    }

    private void partition(HashSet<Individual> examples, Attribute a,
			   HashMap<MOVE,HashSet<Individual> > move2subset,
			   boolean agnostic, boolean destructive){

	for(Iterator<Individual> i = examples.iterator(); i.hasNext();){
	    Individual ind = i.next();

	    MOVE m = ind.m;
	    if (!agnostic) {
		switch(a){
		case PILL: m = ind.pillMove;
		    break;
		case POWER: m = ind.powerPillMove;
		    break;
		case HUNT: m = ind.huntMove;
		    break;
		case FLEE: m = ind.fleeMove;
		    break;
		}
	    }

	    HashSet<Individual> subset = move2subset.get(m);
	    if (subset == null) { subset = new HashSet<Individual>(); }
	    subset.add(ind);
	    if (destructive) { i.remove(); }
	    move2subset.put(m,subset);
	}
    }

    private Node makeTree(HashSet<Individual> examples,
			  HashSet<Attribute> attributes,
			  MOVE parentPlurality){
	//if nothing left
	if (examples.size() == 0) { return new Node(parentPlurality); }

	HashMap<MOVE,Integer> freq = new HashMap<MOVE,Integer>();

	MOVE label = MOVE.NEUTRAL;
	boolean first = true;
	boolean same = true;
	MOVE plurality = MOVE.NEUTRAL;
	int maxFreq = 0;
	for(Individual ind: examples){
	    if (first) {
		label = ind.m;
		first = false;
	    }
	    else if (label != ind.m) { same = false; }

	    Integer n = freq.get(ind.m);
	    if(n == null) { freq.put(ind.m,1); }
	    else { freq.put(ind.m,n+1); }
	    if (freq.get(ind.m) > maxFreq) {
		maxFreq = freq.get(ind.m);
		plurality = ind.m;
	    }
	}

	//if everything is the same
	if (same) { return new Node(label); }

	//if no attributes left or size is too small
	if (attributes.size() == 0 || examples.size() < 10) { return new Node(plurality); }

	//get attribute with most gain
	Attribute a = bestAttribute(examples,attributes);
	//make node for attribute
	Node ans = new Node(a);

	//add child for each value of attribute
	attributes.remove(a);
	HashMap<MOVE,HashSet<Individual> > move2subset = new HashMap<MOVE,HashSet<Individual> >();
	partition(examples,a,move2subset,false,true);

	for(MOVE m: allMoves){
	    HashSet<Individual> subset = move2subset.get(m);
	    if (subset == null) { subset = new HashSet<Individual>(); }
	    Node child = makeTree(subset,attributes,plurality);
	    ans.children.add(child);
	    
	    //Restore examples
	    examples.addAll(subset);
	    move2subset.remove(m);
	}

	//Restore attributes
	attributes.add(a);

	return ans;
    }

    private MOVE traverse(Node n, Game state, int pnode){
	if (n.leaf) { return n.decision; }

	MOVE whichMove = MOVE.NEUTRAL;
	switch(n.a){
	case PILL: whichMove = closer2Pill(state, pnode);
	    break;
	case POWER: whichMove = closer2Power(state, pnode);
	    break;
	case HUNT: whichMove = closer2Ghost(state, pnode);
	    break;
	case FLEE: whichMove = furtherGhost(state, pnode);
	    break;
	}
	int whichChild = 0;
	for(int i = 0; i < 5; i++){
	    if(whichMove == allMoves[i]) { whichChild = i; }
	}
	return traverse(n.children.get(whichChild), state, pnode);
    }

    public MOVE getMove(Game game, long timeDue) {
	int pnode = game.getPacmanCurrentNodeIndex();

	if (!game.isJunction(pnode)) {
	    MOVE lastMoveMade = game.getPacmanLastMoveMade();
	    MOVE[] possibles = game.getPossibleMoves(pnode,lastMoveMade);
	    return possibles[0];
	}

	//Use the tree
	return traverse(this.root, game, pnode);
    }
}
