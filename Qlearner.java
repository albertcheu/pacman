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
    private double ALPHA = 0.8;

    //Discount rate for Q learning
    private double GAMMA = 0.3;

    //Chance to pick a random move, as a percentage
    private int EPSILON = 2;

    //for the function directionalData
    //4 := there is a dangerous ghost on my DIR
    //3 := there is an edible ghost on my DIR
    //2 := there is a pill to my DIR
    //1 := there is a wall to my DIR
    //0 := there is nothing to my DIR
    private int DANGER = 4;
    private int EDIBLE = 3;
    private int PILL = 2;
    private int WALL = 1;
    private int NOTHING = 0;

    private Random rnd=new Random();

    //What we are optimizing
    private ArrayList<ArrayList<Double> > Q;

    public Qlearner(){
	super();

	Q = new ArrayList<ArrayList<Double> >();
	File f = new File("qData");

	if (f.exists()){
	    try{
		BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream("qData")));
		String input=br.readLine();
		int count = 0;
		while(input!=null){//count<allMoves.length*Q.size()){
		    double val = Double.parseDouble(input);

		    if ((count % allMoves.length) == 0) {
			Q.add(new ArrayList<Double>());
		    }
		    Q.get(count/allMoves.length).add(val);

		    input=br.readLine();
		    count++;
		}

		System.out.println(Integer.toString(Q.size()));
	    }

	    catch(IOException ioe){
		System.out.println("File read error!");
		//exit(0);
	    }
	}

	else {

	    //10,000 states, but we need 2^14 space (the way I represent states)
	    for (int i = 0 ; i < Math.pow(2.0,14.0); i++) {
		Q.add(new ArrayList<Double>());
		for (MOVE m: allMoves){
		    //never do move.neutral
		    if (m == MOVE.NEUTRAL){
			Q.get(i).add(-Double.MAX_VALUE);
			continue;
		    }
		    //if pacman has a wall to the DIR, forbid moves in DIR
		    int j = m.ordinal();
		    int data = (i / (int)Math.pow(2.0,(double) (2+3*j) )) % 8;//3 bits
		    if (data == WALL) {
			Q.get(i).add(-Double.MAX_VALUE);
		    }
		    else { Q.get(i).add(0.0); }
		}
	    }

	}

    }

    private boolean danger(Game copy, int n, MOVE dir){
	//Scan to the dir and see if there is an inedible ghost
	while (! copy.isJunction(n)) {

	    for(GHOST g: GHOST.values()){
		if (copy.getGhostCurrentNodeIndex(g) == n) {
		    if (!copy.isGhostEdible(g) &&
			copy.getGhostLastMoveMade(g) == dir.opposite()) {
			return true;
		    }
		}
	    }

	    int newIndex = copy.getNeighbour(n,dir);
	    if (newIndex == -1) {
		MOVE[] possibles = copy.getPossibleMoves(n,dir);
		dir = possibles[0];
	    }
	    n = copy.getNeighbour(n,dir);
	}	
	return false;
    }

    private boolean pill(Game copy, int n, MOVE dir){
	//Scan to the dir and see if there is a pill
	while (! copy.isJunction(n)) {
	    int pIndex = copy.getPillIndex(n);
	    if (pIndex > -1 && copy.isPillStillAvailable(pIndex)) {
		return true;
	    }
	    int newIndex = copy.getNeighbour(n,dir);
	    if (newIndex == -1) { return false; }
	    n = newIndex;
	}	

	return false;
    }

    private boolean edible(Game copy, int n, MOVE dir){
	//Scan to the dir and see if there is an edible ghost
	while (! copy.isJunction(n)) {
	    for(GHOST g: GHOST.values()){
		if (copy.getGhostCurrentNodeIndex(g) == n) {
		    if (copy.isGhostEdible(g) &&
			copy.getGhostLastMoveMade(g) == dir.opposite()) {
			return true;
		    }
		}
	    }

	    int newIndex = copy.getNeighbour(n,dir);
	    if (newIndex == -1) {
		MOVE[] possibles = copy.getPossibleMoves(n,dir);
		dir = possibles[0];
	    }
	    n = copy.getNeighbour(n,dir);

	}	

	return false;
    }

    private int directionalData(Game copy, int pnode, MOVE dir){
	int n = copy.getNeighbour(pnode, dir);
	if (n == -1) { return WALL; }
	if (danger(copy, n, dir)) { return DANGER; }
	if (edible(copy, n, dir)) { return EDIBLE; }
	if (pill(copy, n, dir)) { return PILL; }
	return NOTHING;
    }

    private int state2number(Game copy){
	//Obtain state number s from features in current game copy
	//Uses features checked in reward(copy) as well as the function preceding this one
	int pnode = copy.getPacmanCurrentNodeIndex();	
	int s = 0;
	
	//bit 0: was pacman eaten?
	if (copy.wasPacManEaten()) { s += 1; }
	//bit 1: was a pill eaten?
	if (copy.wasPillEaten()) { s += 2; }

	for (int i = 2; i < 14; i+=3){
	    int moveIndex = (i-2)/3;
	    s += Math.pow(2.0,(double)i)*
		directionalData(copy, pnode, allMoves[moveIndex]);
	}
	
	return s;
    }

    private int reward(int s){
	//Derive reward r given a state number
	int r = 0;

	//bit 0: was pacman eaten?
	if (s%2==1) { r -= 100; }
	//bit 1: was a pill eaten?
	if ((s/2)%2==1) { r += 10; }

	//triplets of bits (directional data)
	for (int i = 2; i < 14; i+=3){
	    int data = (s / (int)Math.pow(2.0,(double)i)) % 8;//3 bits
	    switch(data){
	    case 4://incoming danger
		r -= 5;
		break;
	    case 3://incoming edible
		r += 5;
		break;
	    case 2://pill
		r += 1;
		break;
	    case 1://wall
		r -= 1;
		break;
	    }
	}

	return r;
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

		int pnode = copy.getPacmanCurrentNodeIndex();
		MOVE m = copy.getPacmanLastMoveMade();

		if (copy.isJunction(pnode)) {
		    c++;
		    m = chooseMove(s);
		}

		copy.advanceGame(m,
				 lg.getMove(copy, System.currentTimeMillis()+1));
		int s2 = state2number(copy);
		int r = reward(s2);

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
		FileOutputStream outS=new FileOutputStream("qData",false);
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

	MOVE m = MOVE.NEUTRAL;
	if (!game.isJunction(pnode)) {
	    MOVE lastMoveMade = game.getPacmanLastMoveMade();
	    MOVE[] possibles = game.getPossibleMoves(pnode,lastMoveMade);
	    int s = state2number(game);
	    Game copy = game.copy();
	    m = possibles[0];
	    copy.advanceGame(m,
			     lg.getMove(copy, System.currentTimeMillis()+1));
	    int s2 = state2number(copy);
	    updateQ(s,m,reward(s2),s2);
		    
	}

	else { m = qlearn(game, timeDue); }

	writeFile(game, m);
	return m;
    }
}
