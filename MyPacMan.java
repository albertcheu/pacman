package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;

import java.util.Random;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.EnumMap;

public class MyPacMan extends Controller<MOVE>
{

    private int C = 15;

    private Random rnd=new Random();
    private MOVE[] allMoves = MOVE.values();

    private int score(Game state){

	int pnode = state.getPacmanCurrentNodeIndex();
	int shortest2inedible = Integer.MAX_VALUE;
	int shortest2edible = Integer.MAX_VALUE;

	for(GHOST g: GHOST.values()){
	    int gnode = state.getGhostCurrentNodeIndex(g);
	    int dist = state.getShortestPathDistance(pnode,gnode);
	    if (state.isGhostEdible(g) && dist < shortest2edible)
		{ shortest2edible = dist; }
	    else if (!state.isGhostEdible(g) && dist < shortest2inedible)
		{ shortest2inedible = dist; }
	}

	return state.getScore + shortest2inedible - shortest2edible;
    }

    private MOVE bfs(Game game){
	class Entry{
	    public MOVE firstMove;
	    public Game state;
	    public int depth;

	    Entry(MOVE firstMove, Game state, int depth){
		this.firstMove = firstMove;
		this.state = state;
		this.depth = depth;
	    }
	}

	int bestScore = 0;
	MOVE bestMove = allMoves[0];
	LinkedList<Entry> q = new LinkedList<Entry>();
	q.add(new Entry(bestMove,game,0));

	while(q.size() != 0){
	    Entry e = q.remove();

	    int index = e.state.getPacmanCurrentNodeIndex();
	    MOVE lastMove = e.state.getPacmanLastMoveMade();
	    MOVE[] pacmanMoves = e.state.getPossibleMoves(index,lastMove);

	    ArrayList<MOVE[]> ghostMoveList = new ArrayList<MOVE[]>();
	    for(GHOST g: GHOST.values()){
		lastMove = e.state.getGhostLastMoveMade(g);
		index = e.state.getGhostCurrentNodeIndex(g);
		ghostMoveList.add(e.state.getPossibleMoves(index,lastMove));
	    }

	    for(int i = 0; i < pacmanMoves.length; i++){

		MOVE pmove = pacmanMoves[i];

		ArrayList<EnumMap<GHOST,MOVE>> gmoves = new ArrayList<EnumMap<GHOST,MOVE>>();
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
				gmoves.add(t);
			    }
			}
		    }
		}

		if (gmoves.size() == 0) {
		    t = new EnumMap<GHOST,MOVE>(GHOST.class);
		    t.put(GHOST.values()[0],MOVE.NEUTRAL);
		    t.put(GHOST.values()[1],MOVE.NEUTRAL);
		    t.put(GHOST.values()[2],MOVE.NEUTRAL);
		    t.put(GHOST.values()[3],MOVE.NEUTRAL);
		    gmoves.add(t);
		}

		for (int j = 0; j < gmoves.size(); j++){
		    //System.out.println("x");
		    Game copy = e.state.copy();
		    copy.advanceGame(pmove,gmoves.get(j));

		    if (copy.wasPacManEaten()) { continue; }
		    if (e.depth == C) {
			int currentScore = score(copy);
			if (currentScore >= bestScore) {
			    bestScore = currentScore;
			    bestMove = e.firstMove;
			}
			continue;
		    }

		    Entry neighbor = new Entry(e.firstMove,copy,e.depth+1);
		    if (e.depth == 0) { neighbor.firstMove = pmove; }
		    q.add(neighbor);
		}
	    }
	}

	return bestMove;
    }

    public MOVE getMove(Game game, long timeDue) 
    {

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
	MOVE ans = bfs(game);
	//return allMoves[rnd.nextInt(allMoves.length)];
	return ans;
    }
}
