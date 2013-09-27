package org.siggd.actor;

import org.siggd.Convert;
import org.siggd.Game;
import org.siggd.Level;
import org.siggd.StableContact;
import org.siggd.view.BodySprite;
import org.siggd.view.CompositeDrawable;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;

/**
 * This class can be used as a convenient shortcut for any of the bodies in
 * bodies.json. Just set the Body property to the appropriate body, and the
 * actor will become that body.
 * 
 * @author mysterymath
 * 
 */
public class ConveyorBelt extends Actor {
	private String mTex;
	/**
	 * Constructor. No non-optional parameters may be added to this constructor.
	 * This should contain only properties, and code that MUST run before later
	 * init,
	 * 
	 * @param level
	 *            The level that contains this actor
	 * @param id
	 *            The id
	 */
	private Fixture mLeftCircle;
	private Fixture mRightCircle;
	private Fixture mBox;

	public ConveyorBelt(Level level, long id) {
		super(level, id);
		mName = "ConveyorBelt";
		mTex = "data/gfx/conveyor.png";
		float scale = Game.get().getLevelView().getVScale();
		Vector2 origin = new Vector2(128 / scale, 128 / scale);
		BodyDef bd = new BodyDef();
		bd.position.set(0, 0);
		bd.type = BodyType.StaticBody;
		mBody = getLevel().getWorld().createBody(bd);

		CircleShape circle = new CircleShape();
		circle.setPosition(new Vector2(-0.78f, 0f));
		circle.setRadius(.25f);
		FixtureDef fd = new FixtureDef();
		fd.shape = circle;
		fd.density = 1f;
		fd.restitution = 0.0f;

		mLeftCircle = mBody.createFixture(fd);

		circle = new CircleShape();
		circle.setPosition(new Vector2(0.78f, 0f));
		circle.setRadius(.25f);
		fd = new FixtureDef();
		fd.shape = circle;
		fd.density = 1f;
		fd.restitution = 0.0f;

		mRightCircle = mBody.createFixture(fd);

		PolygonShape box = new PolygonShape();
		box.setAsBox(0.78f, .24f);
		fd = new FixtureDef();
		fd.shape = box;
		fd.density = 1f;
		fd.restitution = 0.0f;

		mBox = mBody.createFixture(fd);

		setProp("Friction", 0f);

		setProp("Anticlockwise", (Integer) 1);
		setProp("Speed", (Float) .5f);

		((CompositeDrawable) mDrawable).mDrawables.add(new BodySprite(mBody, origin, mTex));
	}

	/**
	 * Load resources needed by the actor
	 */
	@Override
	public void loadResources() {
		AssetManager man = Game.get().getAssetManager();
		man.load(mTex, Texture.class);
	}

	/**
	 * Load bodies needed by the actor
	 */
	@Override
	public void loadBodies() {
	}

	@Override
	public void update() {
		Iterable<StableContact> contacts = Game.get().getLevel().getContactHandler()
				.getContacts(mBox);

		Body body;
		Vector2 rot = new Vector2(1, 0);
		Vector2 deltaPos;
		float mult = Convert.getInt(getProp("Anticlockwise")) == 1 ? -1 : 1;
		float speed = Convert.getFloat(getProp("Speed"));
		rot.rotate(Convert.getFloat(getProp("Angle")));
		for (StableContact sc : contacts) {
			Vector2 tempRot = rot.cpy();
			body = sc.getFixtureB().getBody();
			if ((Actor) body.getUserData() instanceof Redirector
					|| (Actor) body.getUserData() instanceof ExplodeBall
					|| (Actor) body.getUserData() instanceof ImplodeBall)
				continue;
			deltaPos = mBody.getPosition();
			deltaPos.sub(body.getPosition());
			if (deltaPos.crs(rot) < 0) {
				tempRot.scl(-1 * mult * speed);
			} else {
				tempRot.scl(mult * speed);
			}
			body.applyForceToCenter(tempRot, true);
		}

		contacts = Game.get().getLevel().getContactHandler().getContacts(mLeftCircle);
		for (StableContact sc : contacts) {
			body = sc.getFixtureB().getBody();
			if ((Actor) body.getUserData() instanceof Redirector
					|| (Actor) body.getUserData() instanceof ExplodeBall
					|| (Actor) body.getUserData() instanceof ImplodeBall)
				continue;
			deltaPos = mBody.getPosition().cpy();
			deltaPos.add(-0.78f, 0f);
			deltaPos.sub(body.getPosition());
			deltaPos.nor();
			deltaPos.rotate(90 * mult);
			deltaPos.scl(speed);
			body.applyForceToCenter(deltaPos, true);
		}

		contacts = Game.get().getLevel().getContactHandler().getContacts(mRightCircle);
		for (StableContact sc : contacts) {
			body = sc.getFixtureB().getBody();
			if ((Actor) body.getUserData() instanceof Redirector
					|| (Actor) body.getUserData() instanceof ExplodeBall
					|| (Actor) body.getUserData() instanceof ImplodeBall)
				continue;
			deltaPos = mBody.getPosition().cpy();
			deltaPos.add(0.78f, 0f);
			deltaPos.sub(body.getPosition());
			deltaPos.nor();
			deltaPos.rotate(90 * mult);
			deltaPos.scl(speed);
			body.applyForceToCenter(deltaPos, true);
		}
	}

	/**
	 * Dispose of the actor's resources
	 */
	@Override
	public void dispose() {
		AssetManager man = Game.get().getAssetManager();
		if(man.containsAsset(mTex)) {
			man.unload(mTex);
		}
	}

	@Override
	public void postLoad() {
	}
}
