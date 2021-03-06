package org.siggd;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;
import org.siggd.Player.ControlType;
import org.siggd.actor.meta.ActorEnum;
import org.siggd.actor.meta.PropScanner;
import org.siggd.editor.Editor;
import org.siggd.platform.Reflector;
import org.siggd.view.LevelView;
import org.siggd.view.MenuView;

import pong.client.core.BodyEditorLoader;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;

/**
 * This class dispatches the main game loop (the Controller in MVC).
 * 
 * A few things to note:
 * 
 * InputProcessors registered with Input should *always* return false.
 * 
 * @author mysterymath
 * 
 */
public class Game implements ApplicationListener {
	/**
	 * Definitions for Game-level state machine
	 * 
	 * @author mysterymath
	 * 
	 */

	public final static int EDIT = 0;
	public final static int PLAY = 1;
	public final static int MENU = 2;
	public final static int LOAD = 3;
	public final static int MAX_PLAYERS = 8;
	public final static boolean RELEASE = true;
	public final static boolean DEBUG = false;
	public final static boolean UNLOCKED = false;

	// loaded from config!!!
	public static boolean CALM = false; // setting does nothing
	public static boolean PRELOAD = false; // setting does nothing

	public static float FPSREC = 60;

	public final String mStartingLevel = "level1";

	// The game's current state
	private int mState;

	private boolean mPaused;

	// The view of the level
	private LevelView mLevelView;
	// The menu renderer
	private MenuView mMenuView;
	// The level
	private Level mLevel;
	// An enumerator for all the actors
	private ActorEnum mActorEnum;
	// A scanner for actor properties
	private PropScanner mPropScanner;
	// The asset manager
	private AssetManager mAssetManager;
	// Input event dispatcher
	private InputMultiplexer mInput;
	// Loads bodies from editor
	private BodyEditorLoader mBodyLoader;
	private ArrayList<Player> mPlayers;
	private PlayerListener mPlayerListener;
	private String mNextLevel;
	private String mTickSound = "data/sfx/tick.ogg";
	private String mNomSound = "data/sfx/nom.wav";
	public ArrayList<String> mHackishLoader;
	public int mLoaderMax;
	private boolean mFlipper = true;
	// Framerate profiler
	private int mInitDelay = 30;
	private long mStartTime;
	private long mEndTime;
	private ArrayList<Long> mRenderTimeHistory;
	private static int SAMPLE_SIZE = 600;
	private boolean mProfileFinished = false;
	private Reflector mReflector;

	/**
	 * Constructor (private)
	 */
	public Game(Reflector reflector) {
		if (theGame == null) {
			theGame = this;
		} else {
			throw new RuntimeException("Cannot have multiple instances of Game.");
		}
		
		mReflector = reflector;
	}

	/**
	 * The singleton object for type Game
	 */
	private static Game theGame;

	/**
	 * Function to get the singleton object
	 * 
	 * @return
	 */
	public static Game get() {
		return theGame;
	}

	/**
	 * The level editor
	 */
	private Editor mEditor;

	/**
	 * Initialize the game
	 */
	@Override
	public void create() {
		mHackishLoader = new ArrayList<String>();

		mPropScanner = new PropScanner("org.siggd.actor");

		// Create the asset manager
		mAssetManager = new AssetManager();
		mAssetManager.setLoader(Level.class, new LevelLoader(new InternalFileHandleResolver()));
		mAssetManager.setLoader(Texture.class, new TextureLoaderWrapper(
				new InternalFileHandleResolver()));
		// Setup input
		mInput = new InputMultiplexer();
		mPlayers = new ArrayList<Player>();

		DebugOutput.enable();
		System.out.println("Controllers: " + Controllers.getControllers().size);
		try {
			ControllerFilterAPI.load();
		} catch (JSONException e) {
			e.printStackTrace();
		}

		// Create the level view
		mLevelView = new LevelView();
		mMenuView = new MenuView();
		// Input
		if (mMenuView.getMenuController() != null) {
			mInput.addProcessor(mMenuView.getMenuController());
		}
		// if (mMenuView.getStage() != null) {
		// mInput.addProcessor(mMenuView.getStage());
		// }

		if (!RELEASE) {
			// Create players the old way if not in release mode
			int i = 0;
			for (Controller controller : Controllers.getControllers()) {
				Player p = new Player(mPlayers.size());
				p.controller = controller;
				p.active = true;
				p.controltype = ControlType.Controller;
				mPlayers.add(p);
				System.out.println("Controller #" + i++ + ": " + controller.getName());
			}
			if (Controllers.getControllers().size == 0) {
				System.out.println("No controllers attached");
				Player p = new Player(mPlayers.size());
				p.active = true;
				p.controltype = ControlType.Arrows;
				mPlayers.add(p);
			}
		}

		// setup the listener that prints events to the console
		mPlayerListener = new PlayerListener();
		Controllers.addListener(mPlayerListener);
		Controllers.addListener(mMenuView.getMenuController());

		// Load physics bodies

		if (RELEASE) {
			mBodyLoader = new BodyEditorLoader(Gdx.files.internal("data/bodies.json"));
		} else {
			mBodyLoader = new BodyEditorLoader(combineBodies());
		}

		// BEGIN: EDITOR
		// Enumerate the available actors
		mActorEnum = new ActorEnum();
		// END: EDITOR
		mLevel = new Level(null);

		// BEGIN: EDITOR
		// Create the editor

		// DebugOutput.disable();
		if (!RELEASE) {
			mEditor = new Editor();
			setLevel(mStartingLevel);
			setState(Game.EDIT);
			// load the select/point image
			mAssetManager.load(mEditor.selectPoint, Texture.class);
		} else {
			FileHandle handleSt = Gdx.files.external(Level.SAVE_FILE);
			if (handleSt.exists()) {
				String json = handleSt.readString();
				if (json.length() > 1) {
					try {
						JSONObject levels = new JSONObject(json);
						Level.unlockModes(levels);
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
			if (PRELOAD) {
				setState(Game.LOAD);
				setLevel("emptylevel");
				mMenuView.setMenu(MenuView.LOADING);
				mHackishLoader.add("level1");
				mHackishLoader.add("level2");
				mHackishLoader.add("charselect");
				mHackishLoader.add("level3");
				mHackishLoader.add("level4");
				mHackishLoader.add("closing");
				mHackishLoader.add("level5");
				mHackishLoader.add("opening");
				mHackishLoader.add("level7");
				mHackishLoader.add("level8");
				mHackishLoader.add("earth");

				mLoaderMax = mHackishLoader.size();
			} else {
				setState(Game.MENU);
				setLevel("earth");
				mLevelView.resetCamera();
				mMenuView.setMenu(MenuView.MAIN);
			}
		}
		// END: EDITOR

		// Load level from JSON

		// BEGIN: EDITOR
		// Load all actor resources
		mActorEnum.loadActorResources();

		// END: EDITOR
		mAssetManager.load(mTickSound, Sound.class);
		mAssetManager.load(mNomSound, Sound.class);

		// Finish loading all the resources
		mAssetManager.finishLoading();

		mRenderTimeHistory = new ArrayList<Long>();
	}

	public String combineBodies() {
		// TODO: This shouldn't be needed at release.
		StringBuilder sbOut = new StringBuilder();
		FileHandle dirHandle;
		if (Gdx.app.getType() == ApplicationType.Android) {
			dirHandle = Gdx.files.internal("data/bodies");
		} else {
			// ApplicationType.Desktop ..
			dirHandle = Gdx.files.internal("./bin/data/bodies");
		}
		sbOut.append("{\"rigidBodies\":[");
		for (FileHandle entry : dirHandle.list()) {
			String s = entry.readString();
			s = s.replaceAll("\\.\\./", "");
			StringBuilder sb = new StringBuilder(s);
			sb.delete(0, 16);
			sb.delete(sb.length() - 22, sb.length());
			sb.append(",");
			sbOut.append(sb.toString());
		}
		sbOut.delete(sbOut.length() - 1, sbOut.length());
		sbOut.append("],\"dynamicObjects\":[]}");
		FileHandle output = new FileHandle(
				"../../Blob-Game/BlobGame-android/assets/data/bodies.json");
		output.writeString(sbOut.toString(), false);
		return sbOut.toString();
	}

	/**
	 * Deinitialize the game
	 */
	@Override
	public void dispose() {
		// BEGIN: EDITOR
		// Destroy the editor
		if (mEditor != null) {
			mEditor.dispose();
		}
		// END: EDITOR

		// Destroy the level view
		mLevelView.dispose();

		mMenuView.dispose();

		// Destroy the asset manager
		mAssetManager.dispose();
	}

	/**
	 * Render the game
	 */
	@Override
	public void render() {
		if (!mHackishLoader.isEmpty()) {
			if (mFlipper = !mFlipper) {
				String level = mHackishLoader.remove(0);
				setLevel(level);
				if (mHackishLoader.isEmpty()) {
					setState(MENU);
					Game.get().getLevelView().resetCamera();
					mMenuView.setMenu(MenuView.MAIN);
				}
			}
		}

		// Load any necessary resources
		if (!mAssetManager.update()) {
			// Show loading screen if not done loading
			return;
		}
		if (mNextLevel != null) {
			setLevel(mNextLevel);
			mNextLevel = null;
		}
		if (mState == PLAY || mState == MENU) {
			mMenuView.update();
			if (!mPaused) {
				mLevel.update();
			}
		}

		if (mEditor != null) {
			mEditor.update();
		}

		if (mState != LOAD && mNextLevel == null) {
			mLevelView.update();
			mLevelView.render();
		}

		if (mState == MENU || mState == LOAD) {
			mMenuView.render();
		}
		if (mState != LOAD && "earth".equals(mLevel.getAssetKey()) && !mProfileFinished) {
			if (mInitDelay <= 0) {
				if (mRenderTimeHistory.size() < SAMPLE_SIZE) {
					long nano = System.nanoTime();
					mRenderTimeHistory.add(nano - mStartTime);
					mStartTime = nano;
				} else {
					assessFramerate();
				}
			} else {
				mInitDelay--;
				mStartTime = System.nanoTime();
			}
		}
	}

	private void assessFramerate() {
		// TODO: Assess Framerate and handle force value accordingly
		long total = 0;
		for (long i : mRenderTimeHistory) {
			total += i;
		}
		if (mRenderTimeHistory.size() > 0) {
			float avg = total / (mRenderTimeHistory.size() * 1000.0f);
			avg /= 1000000.0f;
			System.out.println("fps: " + (1.0f / avg));
			FPSREC = 1 / avg;
		}
		mProfileFinished = true;
	}

	/**
	 * Handle a window resize
	 */
	@Override
	public void resize(int width, int height) {
		mLevelView.onResize(width, height);
		mMenuView.onResize(width, height);
	}

	/**
	 * Handle an externally-generated pause
	 */
	@Override
	public void pause() {
	}

	/**
	 * Handle an externally-generated resume
	 */
	@Override
	public void resume() {
	}

	// ACCESSORS & MUTATORS
	/**
	 * Returns the current state
	 * 
	 * @return the Game's State
	 */
	public int getState() {
		return mState;
	}

	/**
	 * Sets the current state. When setting state to MENU, must explicitly pause
	 * game too
	 * 
	 * @param state
	 *            The new state
	 */
	public void setState(int state) {
		mState = state;
		if (Gdx.input.getInputProcessor() == null && mInput != null) {
			Gdx.input.setInputProcessor(mInput);
		}
		if (state == MENU) {
			mMenuView.giveFocus();
			Gdx.input.setCursorCatched(false);
		} else if (state == EDIT) {
			Gdx.input.setCursorCatched(false);
		} else if (state == PLAY) {
			setPaused(false);
			if (RELEASE) {
				Gdx.input.setCursorCatched(true);
			}
		}
	}

	public void setPaused(boolean pause) {
		mPaused = pause;
	}

	public boolean getPaused() {
		return mPaused;
	}

	/**
	 * Returns the Game's Asset Manager
	 * 
	 * @return the Game's Asset Manager
	 */
	public AssetManager getAssetManager() {
		return mAssetManager;
	}

	/**
	 * Returns the Game's ActorEnum
	 * 
	 * @return Returns the Game's ActorEnum
	 */
	public ActorEnum getActorEnum() {
		return mActorEnum;
	}

	/**
	 * Returns the Game's PropScanner
	 * 
	 * @return
	 */
	public PropScanner getPropScanner() {
		return mPropScanner;
	}

	/**
	 * Returns the Game's Editor
	 * 
	 * @return Returns the Game's Editor
	 */
	public Editor getEditor() {
		return mEditor;
	}

	/**
	 * Returns the Game's Level
	 * 
	 * @return Returns the Game's Level
	 */
	public Level getLevel() {
		return mLevel;
	}
	
	/**
	 * Returns a platform-specific reflection object
	 * @return A platform-specific reflection object
	 */
	public Reflector getReflector() {
		return mReflector;
	}

	/**
	 * Loads and changes the games Level, this may be incompatible with android.
	 * (1/19/2013)
	 * 
	 * @param fileName
	 *            name of the file to be loaded
	 */
	public void setLevel(String fileName) {
		if ("earth".equals(mLevel.getAssetKey()) && !mProfileFinished) {
			assessFramerate();
		}
		boolean genMap = "gen".equals(fileName);
		if (genMap)
			fileName = "base";
		Music music = null;
		String song = null;
		float vol = mLevel.mCurrentVolume;
		Level oldLevel = mLevel;
		if (mLevel != null) {
			mLevel.dispose();
			music = mLevel.mMusic;
			song = (String) mLevel.mProps.get("SongName");
		}
		if (mLevel != null && mLevel.getAssetKey() != null) {
			mAssetManager.unload(mLevel.getAssetKey());
		}
		// TODO loading screen stuff here?
		mAssetManager.load(fileName, Level.class);
		mAssetManager.finishLoading();
		mLevel = mAssetManager.get(fileName, Level.class);

		if (Game.get().getEditor() != null) {
			getEditor().updateWorldProperties();
		}

		// Finish loading the map
		mAssetManager.finishLoading();

		// Load all level resources
		String newSong = (String) mLevel.mProps.get("SongName");

		mLevel.mMusic = music;
		mLevel.mCurrentVolume = vol;
		mLevel.loadResources();

		if (oldLevel != null) {
			oldLevel.stopMusic();
		}

		// Finish loading the level resources
		mAssetManager.finishLoading();
		if (genMap) {
			mAssetManager.finishLoading();
			new LevelGen();
		}

	}

	/**
	 * Used by the level editor to reset level;
	 * 
	 * @param json
	 */
	public void setLevel(JSONObject json) {
		String assetKey = mLevel.getAssetKey();
		if (mLevel != null)
			mLevel.dispose();
		mLevel = null;
		mLevel = new Level(assetKey);
		try {
			mLevel.load(json);
		} catch (JSONException e) {
			System.out.println("Failed to load JSONObject level");
		}
		mLevel.loadResources();
	}

	/**
	 * Loads and changes the games Level, this may be incompatible with android.
	 * (1/19/2013)
	 * 
	 * @param the
	 *            Level object to use
	 */
	public void setLevel(Level l) {
		if (mLevel != null)
			mLevel.dispose();
		mLevel = l;
	}

	/**
	 * Returns the Game's LevelView
	 * 
	 * @return Returns the Game's LevelView
	 */
	public LevelView getLevelView() {
		return mLevelView;
	}

	/**
	 * Returns the Game's MenuView
	 * 
	 * @return Returns the Game's MenuView
	 */
	public MenuView getMenuView() {
		return mMenuView;
	}

	/**
	 * Returns the Game's Body Editor Loader
	 * 
	 * @return Returns the Game's BodyEditorLoader
	 */
	public BodyEditorLoader getBodyEditorLoader() {
		return mBodyLoader;
	}

	/**
	 * Returns the Input
	 * 
	 * @returns the Input
	 */
	public InputMultiplexer getInput() {
		return mInput;
	}

	public int getNumberOfPlayers() {
		return mPlayers.size();
	}

	public boolean playerExists(Player p) {
		return mPlayers.contains(p);
	}

	public ArrayList<Player> getPlayers() {
		return mPlayers;
	}

	public void addPlayer(Player p) {
		mPlayers.add(p);
	}

	public Player getPlayer(int id) {
		for (Player p : mPlayers) {
			if (p.id == id) {
				return p;
			}
		}
		return null;
	}

	public Player getPlayer(Controller c) {
		for (Player p : mPlayers) {
			if (p.controller == c) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Used to get Arrows or WASD player
	 * 
	 * @param t
	 *            ControlType.Controller is ignored, use Arrows or WASD
	 * @return
	 */
	public Player getPlayer(ControlType t) {
		if (t == ControlType.Controller)
			return null;
		for (Player p : mPlayers) {
			if (p.controltype == t) {
				return p;
			}
		}
		return null;
	}

	public void deactivatePlayers() {
		for (int i = 0; i < mPlayers.size(); i++) {
			mPlayers.get(i).active = false;
		}
	}

	public int activePlayersNum() {
		int i = 0;
		for (Player p : mPlayers) {
			if (p.active)
				i++;
		}
		return i;
	}

	public ArrayList<Player> activePlayers() {
		ArrayList<Player> players = new ArrayList<Player>();
		for (Player p : mPlayers) {
			if (p.active)
				players.add(p);
		}
		return players;
	}

	public void setNextLevel(String nextLevel) {
		mNextLevel = nextLevel;
	}

	// TODO: move these functions somewhere more appropriate
	public void playTickSound() {
		Sound sound;
		long soundID;
		if (mAssetManager.isLoaded(mTickSound)) {
			sound = mAssetManager.get(mTickSound, Sound.class);
			soundID = sound.play();
			sound.setPitch(soundID, 1);
			sound.setVolume(soundID, .5f);
		}
	}

	public void playNomSound() {
		Sound sound;
		long soundID;
		if (mAssetManager.isLoaded(mNomSound)) {
			sound = mAssetManager.get(mNomSound, Sound.class);
			soundID = sound.play();
			sound.setPitch(soundID, 1);
			sound.setVolume(soundID, .5f);
		}
	}

	public void saveScreenshot() {
		File f = new File(Gdx.files.getExternalStoragePath() + ".BlobGame/Screenshots/"
				+ System.currentTimeMillis() + ".png");
		FileHandle handle;
		if (!f.exists()) {
			handle = new FileHandle(f);
			Pixmap pixmap = getScreenshot(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			PixmapIO.writePNG(handle, pixmap);
		}

	}

	public Pixmap getScreenshot(int x, int y, int w, int h) {
		Gdx.gl.glPixelStorei(GL10.GL_PACK_ALIGNMENT, 1);

		final Pixmap pixmap = new Pixmap(w, h, Format.RGBA8888);
		ByteBuffer pixels = pixmap.getPixels();
		Gdx.gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, pixels);
		final int numBytes = w * h * 4;
		byte[] lines = new byte[numBytes];

		final int numBytesPerLine = w * 4;
		for (int i = 0; i < h; i++) {
			pixels.position((h - i - 1) * numBytesPerLine);
			pixels.get(lines, i * numBytesPerLine, numBytesPerLine);
		}
		pixels.clear();
		for (int i = 0; i < w * h; i++) {
			lines[i * 4 + 3] = (byte) 255;
		}
		pixels.put(lines);

		return pixmap;
	}

	// I can't believe we didn't have a function for this before
	public void exit() {
		DebugOutput.close();
		Gdx.app.exit();
	}
}