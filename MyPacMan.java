package pacman.entries.pacman;

import pacman.controllers.Controller;
//import pacman.controllers.examples.StarterGhosts;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;

import java.util.Random;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.EnumMap;


/*
  Declare (arbitrary) cutoff C

  best-score = 0
  best-first-move = whatever

  enqueue(whatever, game object)

  While queue or stack isnt empty
  Pop off (first-move, game object) from queue

  Get the player's n possible moves

  For every ghost_j:
  Save its possible moves in list_j

  We have n*list_1*list_2*list_3*list_4 (in total, m combo-moves)

  Make copies of current game object.

  For each i of the m combinations
  Advance copy i using move i

  If the resulting state leads to death, continue

  If depth C is reached, score the state, update best & continue

  If depth = 0, enqueue (i, copy)
  Else enqueue(first-move, copy)

  SCORE is the sum of (or difference between)
  +the number of pills/powerpills eaten
  +the path-length from Pacman to the nearest (edible) ghost

*/

public class MyPacMan extends Controller<MOVE>{

    //private StarterGhosts sg = new StarterGhosts();
    private int C = 25;

    private Random rnd=new Random();
    private MOVE[] allMoves = MOVE.values();

    //Evaluates game state
    private int score(Game state){

	int pnode = state.getPacmanCurrentNodeIndex();
	int shortest2inedible = Integer.MAX_VALUE;
	int shortest2edible = Integer.MAX_VALUE;

	//Find distances to ghosts
	for(GHOST g: GHOST.values()){
	    int gnode = state.getGhostCurrentNodeIndex(g);
	    int dist = state.getShortestPathDistance(pnode,gnode);
	    if (state.isGhostEdible(g) && dist < shortest2edible)
		{ shortest2edible = dist; }
	    else if (!state.isGhostEdible(g) && dist < shortest2inedible)
		{ shortest2inedible = dist; }
	}

	//Complete evaluation based on ghosts & score
	if (shortest2edible == Integer.MAX_VALUE)
	    { return state.getScore() + shortest2inedible; }
	else if (shortest2inedible == Integer.MAX_VALUE)
	    { return state.getScore() - shortest2edible; }
	return state.getScore() + shortest2inedible - shortest2edible;
    }

    private void findGhostMoves(ArrayList<EnumMap<GHOST,MOVE>> ghostMoves,
				ArrayList<MOVE[]> ghostMoveList){
	EnumMap<GHOST,MOVE> t;

	for(int j = 0; j < ghostMoveList.get(0).length; j++){
	    for(int k = 0; k < ghostMoveList.get(1).length; k++){
		for(int l = 0; l < ghostMoveList.get(2).length; l++){
		    for(int m = 0; m < ghostMoveList.get(3).length; m++){

			t = new EnumMap<GHOST,MOVE>(GHOST.class);
			t.put(GHOST.values()[0],ghostMoveList.get(0)[j]);
			t.put(GHOST.values()[1],ghostMoveList.get(1)[k]);
			t.put(GHOST.values()[2],ghostMoveList.get(2)[l]);
			t.put(GHOST.values()[3],ghostMoveList.get(3)[m]);
			ghostMoves.add(t);
		    }
		}
	    }
	}

	if (ghostMoves.size() == 0) {
	    t = new EnumMap<GHOST,MOVE>(GHOST.class);
	    t.put(GHOST.values()[0],MOVE.NEUTRAL);
	    t.put(GHOST.values()[1],MOVE.NEUTRAL);
	    t.put(GHOST.values()[2],MOVE.NEUTRAL);
	    t.put(GHOST.values()[3],MOVE.NEUTRAL);
	    ghostMoves.add(t);
	}

    }

    private MOVE treeSearch(Game game, long timeDue, boolean breadthFirst){

	//Entries in the search queue
	class Entry{
	    //The first move pacman makes to get to this future state
	    public MOVE firstMove;
	    //The game object representing the future state
	    public Game state;
	    //How far into the future we have explored
	    public int depth;

	    //Simple constructor
	    Entry(MOVE firstMove, Game state, int depth){
		this.firstMove = firstMove;
		this.state = state;
		this.depth = depth;
	    }
	}

	//Our answer(s)
	int bestScore = 0;
	MOVE bestMove = allMoves[0];

	//LinkedLists can be both LIFO & FIFO
	LinkedList<Entry> q = new LinkedList<Entry>();
	//Initialize our search space (firstMove=bestMove=dummy value for now)
	Entry init = new Entry(bestMove,game,0);
	if (breadthFirst) { q.add(init); } else { q.push(init); }

	while(q.size() != 0){
	    Entry e = breadthFirst ? q.remove() : q.pop();

	    //Location and available moves for pacman
	    int index = e.state.getPacmanCurrentNodeIndex();
	    MOVE lastMove = e.state.getPacmanLastMoveMade();
	    MOVE[] pacmanMoves = e.state.getPossibleMoves(index,lastMove);
	    //MOVE[] pacmanMoves = e.state.getPossibleMoves(index);

	    //Location and available moves for ghosts

	    //This will have 4 slots, each describing the available moves for each ghost
	    ArrayList<MOVE[]> ghostMoveList = new ArrayList<MOVE[]>();
	    for(GHOST g: GHOST.values()){
		lastMove = e.state.getGhostLastMoveMade(g);
		index = e.state.getGhostCurrentNodeIndex(g);
		ghostMoveList.add(e.state.getPossibleMoves(index,lastMove));
		//ghostMoveList.add(e.state.getPossibleMoves(index));
	    }

	    //This will have ? EnumMaps, each EnumMap pairing every ghost type with a move
	    ArrayList<EnumMap<GHOST,MOVE>> ghostMoves = new ArrayList<EnumMap<GHOST,MOVE>>();
	    findGhostMoves(ghostMoves,ghostMoveList);
	    /*
	    EnumMap<GHOST,MOVE> ghostMove = new EnumMap<GHOST,MOVE>(GHOST.class);
	    ghostMove.put(GHOST.values()[0],MOVE.NEUTRAL);
	    ghostMove.put(GHOST.values()[1],MOVE.NEUTRAL);
	    ghostMove.put(GHOST.values()[2],MOVE.NEUTRAL);
	    ghostMove.put(GHOST.values()[3],MOVE.NEUTRAL);
	    */
	    //System.out.println(""+pacmanMoves.length + "," + ghostMoves.size());

	    //Pair every pacman move with every set of ghost moves
	    for(int i = 0; i < pacmanMoves.length; i++){
		for (int j = 0; j < ghostMoves.size(); j++){

		    //Advance a copy accordingly
		    Game copy = e.state.copy();
		    copy.advanceGame(pacmanMoves[i],ghostMoves.get(j));
		    //copy.advanceGame(pacmanMoves[i],ghostMove);

		    //If we find a state that leads to death, don't bother exploring further
		    if (copy.wasPacManEaten()) { continue; }
		    
		    //If we reach the cutoff
		    if (e.depth == C) {
			//Evaluate state (update best answer if necessary)
			int currentScore = score(copy);
			if (currentScore >= bestScore) {
			    bestScore = currentScore;
			    bestMove = e.firstMove;
			}
			//Don't go deeper
			continue;
		    }

		    //Go deeper
		    Entry child = new Entry(e.firstMove,copy,e.depth+1);
		    //Save the first move we make
		    if (e.depth == 0) { child.firstMove = pacmanMoves[i]; }

		    if (breadthFirst) { q.add(child); } else { q.push(child); }
		}
	    }
	}

	return bestMove;
    }

    public MOVE getMove(Game game, long timeDue) {
	MOVE ans;

	ans = treeSearch(game,timeDue,false);

	return ans;
    }
}
