/* This code is part of WoT, a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 2 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */
package plugins.WoT;

import java.util.Date;

import freenet.support.CurrentTimeUTC;


/**
 * The score of an Identity in an OwnIdentity's trust tree.
 * A score is the actual rating of how much an identity can be trusted from the point of view of the OwnIdentity which owns the score.
 * If the Score is negative, the identity is considered malicious, if it is zero or positive, it is trusted. 
 * 
 * @author xor (xor@freenetproject.org), Julien Cornuwel (batosai@freenetproject.org)
 */
public final class Score {

	/** Capacity is the maximum amount of points an identity can give to an other by trusting it. 
	 * 
	 * Values choice :
	 * Advogato Trust metric recommends that values decrease by rounded 2.5 times.
	 * This makes sense, making the need of 3 N+1 ranked people to overpower
	 * the trust given by a N ranked identity.
	 * 
	 * Number of ranks choice :
	 * When someone creates a fresh identity, he gets the seed identity at
	 * rank 1 and freenet developpers at rank 2. That means that
	 * he will see people that were :
	 * - given 7 trust by freenet devs (rank 2)
	 * - given 17 trust by rank 3
	 * - given 50 trust by rank 4
	 * - given 100 trust by rank 5 and above.
	 * This makes the range small enough to avoid a newbie
	 * to even see spam, and large enough to make him see a reasonnable part
	 * of the community right out-of-the-box.
	 * Of course, as soon as he will start to give trust, he will put more
	 * people at rank 1 and enlarge his WoT.
	 */
	public static final int capacities[] = {
			100,// Rank 0 : Own identities
			40,	// Rank 1 : Identities directly trusted by ownIdenties
			16, // Rank 2 : Identities trusted by rank 1 identities
			6,	// So on...
			2,
			1	// Every identity above rank 5 can give 1 point
	};			// Identities with negative score have zero capacity
	
	/** The OwnIdentity which assigns this score to the target */
	private final OwnIdentity mTreeOwner;
	
	/** The Identity which is rated by this score */
	private final Identity mTarget;
	
	/** The actual score of the Identity. Used to decide if the OwnIdentity sees the Identity or not */
	private int mValue;
	
	/** How far the Identity is from the tree's root. Tells how much point it can add to its trustees score. */
	private int mRank;
	
	/** How much point the target Identity can add to its trustees score. Depends on its rank AND the trust given by the tree owner.
	 * If the tree owner sets a negative trust on the target identity, it gets zero capacity, even if it has a positive score. */
	private int mCapacity;
	
	
	/**
	 * The date when this score was created. Stays constant if the value of this score changes.
	 */
	private Date mCreationDate; // FIXME: Add "final" as soon as we remove the code for upgrading legacy databases.
	
	/**
	 * The date when the value, rank or capacity was last changed.
	 */
	private Date mLastChangedDate;
	
	/**
	 * Get a list of fields which the database should create an index on.
	 */
	protected static String[] getIndexedFields() {
		return new String[] { "mTreeOwner", "mTarget" };
	}
	
	/**
	 * Creates a Score from given parameters. Only for being used by the WoT package and unit tests, not for user interfaces!
	 * 
	 * @param myTreeOwner The owner of the trust tree
	 * @param myTarget The Identity that has the score
	 * @param myValue The actual score of the Identity. 
	 * @param myRank How far the Identity is from the tree's root. 
	 * @param myCapacity How much point the target Identity can add to its trustees score.
	 */
	public Score(OwnIdentity myTreeOwner, Identity myTarget, int myValue, int myRank, int myCapacity) {
		if(myTreeOwner == null)
			throw new NullPointerException();
			
		if(myTarget == null)
			throw new NullPointerException();
			
		mTreeOwner = myTreeOwner;
		mTarget = myTarget;
		setValue(myValue);
		setRank(myRank);
		setCapacity(myCapacity);
		
		mCreationDate = CurrentTimeUTC.get();
		// mLastChangedDate = CurrentTimeUTC.get(); <= setValue() etc do this already.
	}
	
	@Override
	public synchronized String toString() {
		// This function locks very much stuff and is synchronized. The lock on Score objects should always be taken last by our locking policy right now,
		// otherwise this function might be dangerous to be used for example with logging. Therefore TODO: Ensure that locks on Score are really taken last.
		
		/* We do not synchronize on target and TreeOwner because nickname changes are not allowed, the only thing which can happen
		 * is that we get a blank nickname if it has not been received yet, that is not severe though.*/
		
		return getTarget().getNickname() + " has " + getScore() + " points in " + getTreeOwner().getNickname() + "'s trust tree" +
				"(rank : " + getRank() + ", capacity : " + getCapacity() + ")";
	}

	/**
	 * @return in which OwnIdentity's trust tree this score is
	 */
	public OwnIdentity getTreeOwner() {
		return mTreeOwner;
	}

	/**
	 * @return Identity that has this Score
	 */
	public Identity getTarget() {
		return mTarget;
	}

	/**
	 * @return the numeric value of this Score
	 */
	/* XXX: Rename to getValue */
	public synchronized int getScore() {
		return mValue;
	}

	/**
	 * Sets the numeric value of this Score.
	 */
	protected synchronized void setValue(int newValue) {
		if(mValue == newValue)
			return;
		
		mValue = newValue;
		mLastChangedDate = CurrentTimeUTC.get();
	}

	/**
	 * @return How far the target Identity is from the trust tree's root.
	 */
	public synchronized int getRank() {
		return mRank;
	}

	/**
	 * Sets how far the target Identity is from the trust tree's root.
	 */
	protected synchronized void setRank(int newRank) {
		if(newRank < -1)
			throw new IllegalArgumentException("Illegal rank.");
		
		if(newRank == mRank)
			return;
		
		mRank = newRank;
		mLastChangedDate = CurrentTimeUTC.get();
	}

	/**
	 * @return how much points the target Identity can add to its trustees score
	 */
	public synchronized int getCapacity() {
		return mCapacity;
	}

	/**
	 * Sets how much points the target Identity can add to its trustees score.
	 */
	protected synchronized void setCapacity(int newCapacity) {
		if(newCapacity < 0)
			throw new IllegalArgumentException("Negative capacities are not allowed.");
		
		if(newCapacity == mCapacity)
			return;
		
		mCapacity = newCapacity;
		mLastChangedDate = CurrentTimeUTC.get();
	}
	
	/**
	 * Gets the {@link Date} when this score object was created. The date of creation does never change for an existing score object, so if the value, rank
	 * or capacity of a score changes then its date of creation stays constant.
	 */
	public synchronized Date getDateOfCreation() {
		return mCreationDate;
	}
	
	/**
	 * Gets the {@link Date} when the value, capacity or rank of this score was last changed.
	 */
	public synchronized Date getDateOfLastChange() {
		return mLastChangedDate;
	}
	

	/**
	 * Only for being used in upgradeDatabase(). FIXME: Remove when we leave the beta stage
	 */
	protected synchronized void initializeDates(Date date) {
		mCreationDate = date;
		mLastChangedDate = date;
	}
	
	/**
	 * Test if two scores are equal.
	 * Scores are considered equal if:
	 * - They are from the same tree owner to the same target
	 * - They have the same value
	 * - They have the same rank
	 * - They have the same capacity
	 * 
	 * The creation date and last changed date are NOT compared.
	 */
	public boolean equals(Object obj) {
		if(!(obj instanceof Score))
			return false;
		
		Score other = (Score)obj;
		
		if(other == this)
			return true;

		if(getScore() != other.getScore())
			return false;
		
		if(getRank() != other.getRank())
			return false;
		
		if(getCapacity() != other.getCapacity())
			return false;
		
		// Compare the involved identities after the numerical values because getting them might involve activating objects from the database.
		
		if(getTreeOwner() != other.getTreeOwner())
			return false;
		
		if(getTarget() != other.getTarget())
			return false;
		
		return true;
	}

}
