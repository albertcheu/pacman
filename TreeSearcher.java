package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.controllers.examples.Legacy;
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

  Make copies of current game object.

  For each move
  Advance copy i using move i

  If the resulting state leads to death, continue

  If depth C is reached, score the state, update best & continue

  If depth = 0, enqueue (i, copy)
  Else enqueue(first-move, copy)

  SCORE is
  +game's score
  +the path-length from Pacman to the nearest dangerous ghost
  -the path-length from Pacman to the nearest edible ghost
  -the path-length from Pacman to the nearest pill
*/

public class TreeSearcher extends Controller<MOVE>{

    private Legacy lg = new Legacy();
    private int C = 4;
    private Random rnd=new Random();
    private MOVE[] allMoves = MOVE.values();

    //boolean flag tells whether to use bounded-BFS (true) or DLS (false)
    private boolean breadthFirst;

    public TreeSearcher(boolean b){
	super();
	this.breadthFirst = b;
    }

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

	int shortest2pill = Integer.MAX_VALUE;
	//Find distances to pills
	for(int i: state.getActivePillsIndices()){
	    int dist = state.getShortestPathDistance(pnode,i);
	    if (dist < shortest2pill) { shortest2pill = dist; }
	}

	//Complete evaluation based on ghosts & score
	if (shortest2edible == Integer.MAX_VALUE) { shortest2edible = 0; }
	else if (shortest2inedible == Integer.MAX_VALUE) { shortest2inedible = 0; }
	return state.getScore() + shortest2inedible - shortest2edible - shortest2pill;
    }

    //Returns first step in best sequence
    private MOVE treeSearch(Game game, long timeDue){

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
	if (this.breadthFirst) { q.add(init); } else { q.push(init); }

	while(q.size() != 0){
	    Entry e = this.breadthFirst ? q.remove() : q.pop();

	    //Location and available moves for pacman
	    int index = e.state.getPacmanCurrentNodeIndex();
	    MOVE lastMove = e.state.getPacmanLastMoveMade();
	    MOVE[] pacmanMoves = e.state.getPossibleMoves(index,lastMove);

	    EnumMap<GHOST,MOVE> ghostMove = lg.getMove(e.state,
						       System.currentTimeMillis()+4);

	    //Pair every pacman move with ghost move
	    for(int i = 0; i < pacmanMoves.length; i++){

		//Advance a copy accordingly
		Game copy = e.state.copy();
		copy.advanceGame(pacmanMoves[i],ghostMove);

		//If we find a state that leads to death, don't bother exploring
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
		int childDepth = ((pacmanMoves.length>1)?e.depth+1:e.depth);
		Entry child = new Entry(e.firstMove,copy,childDepth);
		//Save the first move we make
		if (e.depth == 0) { child.firstMove = pacmanMoves[i]; }

		if (this.breadthFirst) { q.add(child); } else { q.push(child); }
	    }

	}

	return bestMove;
    }

    public MOVE getMove(Game game, long timeDue) {
	int index = game.getPacmanCurrentNodeIndex();
	MOVE lastMove = game.getPacmanLastMoveMade();
	MOVE[] pacmanMoves = game.getPossibleMoves(index,lastMove);
	if (pacmanMoves.length == 1) { return pacmanMoves[0]; }

	MOVE ans;

	ans = treeSearch(game,timeDue);

	return ans;
    }
}
