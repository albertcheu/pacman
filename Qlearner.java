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

public class Qlearner extends Controller<MOVE>{

    private MOVE[] allMoves = MOVE.values();
    private Legacy lg = new Legacy();

    //Lookahead cutoff
    private int C = 4;

    //Learning rate
    private double ALPHA = 0.2;

    //Discount rate for Q learning
    private double GAMMA = 0.3;

    //Chance to pick a random move, as a percentage
    private int EPSILON = 2;

    private Random rnd=new Random();

    //What we are optimizing
    private ArrayList<ArrayList<Double> > Q;


    public Qlearner(){

	super();	

	this.Q = new ArrayList<ArrayList<Double> >();
	//2^16 states
	for (int i = 0 ; i < Math.pow(2.0,16.0); i++) {
	    this.Q.add(new ArrayList<Double>());
	    for (int j = 0; j < allMoves.length; j++) {
		this.Q.get(i).add((double)rnd.nextInt(30));
	    }
	}

	File f = new File("qData");
	if (f.exists()){
	    try{
		BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream("trainingData")));
		String input=br.readLine();
		int count = 0;
		while(input!=null){
		    double val = Doulbe.parseDouble(input);
		    this.Q.get(count/allMoves.length).set(count%allMoves.length,val);
		    count++;
		}
	    }

	    catch(IOException ioe){}
	}


    }

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

    private int state2number(Game copy){
	//Obtain state number s from features in current game copy
	//Uses features checked in reward(copy) as well as the four functions preceding this one
	int pnode = copy.getPacmanCurrentNodeIndex();

	int s = closer2Ghost(copy, pnode).ordinal();
	s += 8 * furtherGhost(copy, pnode).ordinal();
	s += 64 * closer2Pill(copy, pnode).ordinal();
	s += 512 * closer2Power(copy, pnode).ordinal();
	s += (copy.wasPillEaten()?4096:0);
	s += (copy.wasPowerPillEaten()?8192:0);

	for(GHOST g: GHOST.values()){
	    if (copy.wasGhostEaten(g)) {
		s += 16384;
		break;
	    }
	}
	s += (copy.wasPillEaten()?32768:0);
	
	return s;
    }

    private MOVE chooseMove(int s){
	//epsilon-greedily choose a MOVE m from Q using s

	//roll a 100 sided die
	int x = rnd.nextInt(100);
	//if the number is under EPSILON, pick a random move
	if (x <= EPSILON) {
	    return allMoves[rnd.nextInt(allMoves.length)];
	}
	//Otherwise do the following

	//Access the s-th row of Q
	ArrayList<Double> row = Q.get(s);
	//Loop thru columns and obtain best entry
	//Get that column index i
	//return allMoves[i]
	double bestVal = -Double.MAX_VALUE;
	int bestIndex = 0;
	for (int i = 0; i < row.size(); i++){
	    if (row.get(i) > bestVal) {
		bestVal = row.get(i);
		bestIndex = i;
	    }
	}
	return allMoves[bestIndex];
    }
    
    private int reward(Game copy){
	//Derive reward r for new state
	int r = 0;

	if (copy.wasPillEaten()) { r += 5; }
	if (copy.wasPowerPillEaten()) { r += 10; }
	for (GHOST g: GHOST.values()) {
	    if (copy.wasGhostEaten(g)) { r += 15; }
	}
	if (copy.wasPacManEaten()) { r -= 70; }

	return r;
    }

    private double expectation(int s2){
	//Access the s2-th row of Q
	ArrayList<Double> row = Q.get(s2);
	//Loop thru columns and return best entry
	double bestVal = -Double.MAX_VALUE;

	for (int i = 0; i < row.size(); i++){
	    if (row.get(i) > bestVal) {
		bestVal = row.get(i);
	    }
	}
	return bestVal;
    }

    private void updateQ(int s, MOVE m, int r, int s2){
	//Update Q using s, move we made, the reward, and the next state
	double oldQ = Q.get(s).get(m.ordinal());
	double newQ = oldQ + ALPHA * (r + GAMMA * expectation(s2) - oldQ);
	Q.get(s).set(m.ordinal(), newQ);
    }

    /*
      As in other controllers, determine turns to make

      If current testing state is not at a junction, keep on making non-rev move
      Else, q learn

      Until we run out of time,
        Make copy of game object
        Until C junctions have been hit,
  	  Obtain state number s from features in current game
	  epsilon-greedily choose a MOVE m from Q using s
	  Advance copy
	  Derive reward r for new state
	  Update Q using s, move we made, and the reward
     */


    private MOVE qlearn(Game game, long timeDue) {
	//We're at a junction

	while(timeDue-4 > System.currentTimeMillis()){
	    Game copy = game.copy();
	    int s = state2number(copy);
	    int c = 0;
	    while(c < C){

		if (copy.gameOver()){ break; }

		//In tunnel
		int pnode = copy.getPacmanCurrentNodeIndex();
		MOVE lastMove = copy.getPacmanLastMoveMade();
		if (! copy.isJunction(pnode)) {
		    copy.advanceGame(lastMove,
				     lg.getMove(copy, System.currentTimeMillis()+1));
		    continue;
		}

		//At junction
		c++;
		MOVE m = chooseMove(s);
		copy.advanceGame(m,
				 lg.getMove(copy, System.currentTimeMillis()+1));
		int r = reward(copy);

		int s2 = state2number(copy);
		updateQ(s,m,r,s2);
		s = s2;
	    }
	}
	return chooseMove(state2number(game));
    }

    private void writeFile(Game game, MOVE m){
	Game copy = game.copy();
	copy.advanceGame(m, lg.getMove(copy, System.currentTimeMillis()+1));
	if (copy.gameOver()) {
	    try{
		FileOutputStream outS=new FileOutputStream("qData",true);
		PrintWriter pw=new PrintWriter(outS);

		for(int i = 0; i < Q.size(); i++){
		    for(int j = 0; j < allMoves.length; j++){
			pw.println(Q.get(i).get(j).toString());
			pw.flush();
		    }
		}

		outS.close();
	    }
	    catch(IOException ioe){}
	}
    }

    public MOVE getMove(Game game, long timeDue) {
	int pnode = game.getPacmanCurrentNodeIndex();

	if (!game.isJunction(pnode)) {
	    MOVE lastMoveMade = game.getPacmanLastMoveMade();
	    MOVE[] possibles = game.getPossibleMoves(pnode,lastMoveMade);
	    return possibles[0];
	}

	//else
	return qlearn(game, timeDue);
    }
}
