package org.siggd.actor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.siggd.Convert;
import org.siggd.Game;
import org.siggd.Level;
import org.siggd.Player;
import org.siggd.Timer;
import org.siggd.view.BodySprite;
import org.siggd.view.CompositeDrawable;
import org.siggd.view.LevelView;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;

public class Spawner extends Actor {
	private ArrayList<Actor> mSpawnees;
	private String mTex;
	private Timer mSpawnTimer;
	private int maxBlobs;

	public Spawner(Level level, long id) {
		super(level, id);
		mSpawnees = new ArrayList<Actor>();
		mName = "Spawner";
		mTex = "data/" + Game.get().getBodyEditorLoader().getImagePath(mName);
		Vector2 origin = new Vector2();
		mBody = makeBody(mName, 256, BodyType.StaticBody, origin, false);
		((CompositeDrawable) mDrawable).mDrawables.add(new BodySprite(mBody, origin, mTex));
		maxBlobs = Game.get().getNumberOfPlayers();

		mSpawnTimer = new Timer();
		mSpawnTimer.setTimer(Convert.getInt(this.getProp("Rate")));
		mSpawnTimer.unpause();
		this.setProp("Blob Spawner", 0);
		this.setProp("Rate", 30);
		this.setProp("Exit Velocity", 2);
	}

	@Override
	public void loadResources() {
		AssetManager man = Game.get().getAssetManager();
		man.load(mTex, Texture.class);

	}

	@Override
	public void update() {
		super.update();
		mSpawnTimer.update();
		if (mSpawnees.size() > 0 && mSpawnTimer.isTriggered()) {
			spawnActor();
			mSpawnTimer.reset();
		}
	}

	@Override
	public void loadBodies() {
	}

	@Override
	public void postLoad() {
		if ("earth".equals(Game.get().getLevel().getAssetKey())){
			maxBlobs = 8;
		}
		// find all actors that are pointing to this spawner
		for (Actor a : this.getLevel()) {
			if (this.getId() == Convert.getInt(a.getProp("Spawner"))) {
				mSpawnees.add(a);
				a.setActive(false);
			}
		}
		// sort by the Y val
		Collections.sort(mSpawnees, new Comparator<Actor>() {
			@Override
			public int compare(Actor o1, Actor o2) {
				return (int) (o1.getY() - o2.getY());
			}
		});
		// check whether it is a blob spawner
		if (Convert.getInt(this.getProp("Blob Spawner")) == 1) {
			for (int i = 0; i < maxBlobs; i++) {
				// construct blob
				Blob blob = new Blob(this.getLevel(), this.getLevel().getId());
				blob.setProp("Player ID", i);
				// assign blob to player
				Player player = Game.get().getPlayer(i);
				if (player != null) {
					player.mActor = blob;
				}
				blob.setActive(false);
				// set the layer to the layer of the placeholder blob
				int layer = Convert.getInt(mLevel.getBlobs(false).get(0).getProp("Layer"));
				blob.setProp("Layer", (Integer) layer);
				mSpawnees.add((int) Math.floor(Math.random() * mSpawnees.size()), blob);
			}
			if (mSpawnees.size() > 0 && mSpawnees.get(0) instanceof Blob) {
				spawnActor();
			}
		}

		LevelView lv = Game.get().getLevelView();

		if (mLevel.getAssetKey() != null && Convert.getInt(this.getProp("Blob Spawner")) == 1) {
			lv.setCameraPosition(mBody.getPosition());
			if (Game.get().getState() != Game.MENU) {
				lv.positionCamera(false);
			}
		}
	}

	public void addToSpawn(Actor a) {
		if (!mSpawnees.contains(a)) {
			if (a.isActive()) {
				a.setActive(false);
			}
			if (mSpawnees.size() == 0) {
				mSpawnTimer.reset();
			}
			mSpawnees.add(a);
		}
	}

	private void spawnActor() {
		Actor spawnee = mSpawnees.get(0);
		if (spawnee instanceof Blob) {
			if (!getLevel().getBlobs(false).contains(spawnee)) {
				this.getLevel().addActor(spawnee);
			}
		}
		Vector2 pos = mBody.getPosition();
		spawnee.setProp("X", pos.x);
		spawnee.setProp("Y", pos.y);
		spawnee.setProp("Angle", mBody.getAngle());
		// actor is now spawned
		spawnee.setActive(true);
		// spawner defaults facing up, hence: (0,1)
		Vector2 vel = new Vector2(0, Convert.getInt(getProp("Exit Velocity")));
		vel.rotate(Convert.getDegrees(mBody.getAngle()));
		spawnee.setVelocityX(vel.x);
		spawnee.setVelocityY(vel.y);
		if (spawnee instanceof Blob) {
			if (((Blob) spawnee).isSolid()) {
				((Blob) spawnee).transform();
			}
		}
		mSpawnees.remove(spawnee);
	}

	public int blobsContained() {
		int count = 0;
		for (Actor a : mSpawnees) {
			if (a instanceof Blob) {
				count++;
			}
		}
		return count;
	}

	@Override
	public void setProp(String name, Object val) {
		// undefined behavior if game is running
		if (name.equals("Rate")) {
			mSpawnTimer.setTimer(Convert.getInt(val));
		}
		super.setProp(name, val);
	}
}