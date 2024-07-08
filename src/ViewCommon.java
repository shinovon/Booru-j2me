/**
 * Copyright (c) 2021 Fyodor Ryzhov
 * Copyright (c) 2024 Arman Jussupgaliyev
 */
import java.io.IOException;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

// from njtai
public class ViewCommon extends Canvas implements Runnable {
	protected float zoom = 1;
	protected float x = 0;
	protected float y = 0;

	protected Thread loader;
	protected boolean error;

	static Image slider;
	
	private boolean hwa;
	
	// SWR only
	private Image toDraw;
	private Image orig;

	private boolean firstDraw = true;

	private boolean resizing;


	/**
	 * Creates the view.
	 * 
	 * @param emo  Object with data.
	 * @param prev Previous screen.
	 * @param page Number of page to start.
	 */
	public ViewCommon(boolean hwa) {
		this.hwa = hwa;
		reload();
		setFullScreenMode(true);
		if (slider == null) {
			try {
				slider = Image.createImage("/slider.png");
			} catch (IOException e) {
				slider = null;
			}
		}
	}

	/**
	 * Loads an image, optionally ignoring the cache.
	 * 
	 * @param n Number of image (not page!) [0; bIApp.chapterPages)
	 * @return Data of loaded image.
	 * @throws InterruptedException
	 */
	protected final byte[] getImage() throws InterruptedException {
		try {
			return bIApp.getPostImage(null);
		} catch (Exception e) {
		}
		return null;

	}
	
	private final byte[] getResizedImage(int size) {
		String s = ";tw="+(getWidth()*size)+";th="+(getHeight()*size);
		try {
			return bIApp.getPostImage(s);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	long lastTime = System.currentTimeMillis();

	public final void run() {
		try {
			synchronized (this) {
				error = false;
				zoom = 1;
				x = 0;
				y = 0;
				reset();
				try {
					prepare();
					repaint();
					resize(1);
					zoom = 1;
				} catch (Exception e) {
					error = true;
					e.printStackTrace();
				}
				repaint();
			}
		} catch (OutOfMemoryError e) {
			bIApp.display(null);
			try {
				Thread.sleep(100);
			} catch (Exception ignored) {}
			bIApp.display(new Alert("Error", "Not enough memory to continue viewing.", null,
					AlertType.ERROR));
			return;
		}
	}

	protected void limitOffset() {
		if (hwa) return;
		int hw = toDraw.getWidth() / 2;
		int hh = toDraw.getHeight() / 2;
		if (x < -hw) x = -hw;
		if (x > hw) x = hw;
		if (y < -hh) y = -hh;
		if (y > hh) y = hh;
	}

	/**
	 * Clears any data, used for rendering.
	 */
	protected void reset() {
		if (hwa) return;
		toDraw = null;
		orig = null;
	}
	
	void prepare() throws InterruptedException {}

	/**
	 * Called when image must change it's zoom.
	 * 
	 * @param size New zoom to apply.
	 */
	protected void resize(int size) {
		if (hwa) return;
		resizing = true;
		try {
			toDraw = null;
			System.gc();
			repaint();
			Image origImg;
			if (!bIApp.onlineResize && bIApp.keepBitmap && orig != null && orig.getHeight() != 1 && orig.getWidth() != 1) {
				origImg = orig;
			} else {
				int l = -1;
				byte[] b;
				try {
					b = bIApp.onlineResize ? getResizedImage(size) : getImage();
					l = b.length;
					origImg = Image.createImage(b, 0, b.length);
					b = null;
					System.gc();
				} catch (RuntimeException e) {
					e.printStackTrace();
					System.out.println("Failed to decode an image in resizing. Size=" + l + "bytes");
					origImg = null;
				}
			}
			resizing = false;
			if (origImg == null) {
				error = true;
				toDraw = null;
				return;
			}
			
			if (!bIApp.onlineResize) {
				int h = getHeight();
				int w = (int) (((float) h / origImg.getHeight()) * origImg.getWidth());
	
				if (w > getWidth()) {
					w = getWidth();
					h = (int) (((float) w / origImg.getWidth()) * origImg.getHeight());
				}
	
				h = h * size;
				w = w * size;
				toDraw = bIApp.resize(origImg, w, h);
			} else {
				toDraw = origImg;
			}
		} catch (Throwable e) {
			e.printStackTrace();
			resizing = false;
			error = true;
			toDraw = null;
			return;
		}
	}
	
	protected void paint(Graphics g) {
		if (hwa) return;
		try {
			Font f = bIApp.smallfont;
			g.setFont(f);
			if (toDraw == null) {
				if (firstDraw) {
					firstDraw = false;
					g.setGrayScale(0);
					g.fillRect(0, 0, getWidth(), getHeight());
				}
				paintNullImg(g, f);
			} else {
				// bg fill
				g.setGrayScale(0);
				g.fillRect(0, 0, getWidth(), getHeight());
				limitOffset();
				if (zoom != 1) {
					g.drawImage(toDraw, (int) x + getWidth() / 2, (int) y + getHeight() / 2,
							Graphics.HCENTER | Graphics.VCENTER);
				} else {
					g.drawImage(toDraw, (getWidth() - toDraw.getWidth()) / 2, (getHeight() - toDraw.getHeight()) / 2,
							0);
				}
			}
			// touch captions
			if (hasPointerEvents() && touchCtrlShown) {
				drawTouchControls(g, f);
			}
			paintHUD(g, f, true);
		} catch (Exception e) {
			e.printStackTrace();
			try {
				bIApp.display(new Alert("Repaint error", e.toString(), null, AlertType.ERROR));
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	String[] touchCaps = new String[] { "x1", "x2", "x3", "<-", "goto", "->", "Back" };

	boolean touchCtrlShown = true;

	protected void reload() {
		if (hwa) return;
		toDraw = null;
		System.gc();
		loader = new Thread(this);
		loader.start();
	}

	/**
	 * Is there something to draw?
	 * 
	 * @return False if view is blocked.
	 */
	public boolean canDraw() {
		return toDraw != null;
	}

	protected final void keyPressed(int k) {
		k = qwertyToNum(k);
		if (k == -7 || k == KEY_NUM9) {
			try {
				if (loader != null && loader.isAlive()) {
					loader.interrupt();
				}
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
			bIApp.display(null);
			return;
		}
//		if (!canDraw()) {
//			repaint();
//			return;
//		}

		if (!resizing) {
			// zooming via *0#
			if (k == KEY_STAR) {
				zoom = 1;
				bIApp.midlet.start(bIApp.RUN_ZOOM_VIEW);
			}
			if (k == KEY_NUM0) {
				zoom = 2;
				bIApp.midlet.start(bIApp.RUN_ZOOM_VIEW);
			}
			if (k == KEY_POUND) {
				zoom = 3;
				bIApp.midlet.start(bIApp.RUN_ZOOM_VIEW);
			}
	
			// zoom is active
			if (zoom != 1) {
				if (k == -5) {
					zoom++;
					if (zoom > 3)
						zoom = 1;
	
					resize((int) zoom);
				} else if (k == -1 || k == KEY_NUM2 || k == 'w') {
					// up
					y += getHeight() * panDeltaMul() / 4;
				} else if (k == -2 || k == KEY_NUM8 || k == 's') {
					y -= getHeight() * panDeltaMul() / 4;
				} else if (k == -3 || k == KEY_NUM4 || k == 'a') {
					x += getWidth() * panDeltaMul() / 4;
				} else if (k == -4 || k == KEY_NUM6 || k == 'd') {
					x -= getWidth() * panDeltaMul() / 4;
				}
			} else {
				// zoom inactive
				if (k == -5) {
					zoom = 2;
					x = 0;
					y = 0;
					bIApp.midlet.start(bIApp.RUN_ZOOM_VIEW);
				}
			}
		}

		repaint();
	}

	protected final void keyRepeated(int k) {
		k = qwertyToNum(k);
		if (!canDraw()) {
			repaint();
			return;
		}
		// zoom is active
		if (zoom != 1) {
			if (k == -1 || k == KEY_NUM2 || k == 'w') {
				// up
				y += getHeight() * panDeltaMul() / 4;
			} else if (k == -2 || k == KEY_NUM8 || k == 's') {
				y -= getHeight() * panDeltaMul() / 4;
			} else if (k == -3 || k == KEY_NUM4 || k == 'a') {
				x += getWidth() * panDeltaMul() / 4;
			} else if (k == -4 || k == KEY_NUM6 || k == 'd') {
				x -= getWidth() * panDeltaMul() / 4;
			}
		}

		repaint();
	}

	/**
	 * <ul>
	 * <li>0 - nothing
	 * <li>1 - zoom x1
	 * <li>2 - zoom x2
	 * <li>3 - zoom x3
	 * <li>4 - prev
	 * <li>5 - goto
	 * <li>6 - next
	 * <li>7 - return
	 * <li>8 - zoom slider
	 * </ul>
	 */
	int touchHoldPos = 0;
	int lx, ly;
	int sx, sy;

	protected final void pointerPressed(int tx, int ty) {
		if (!canDraw() && ty > getHeight() - 50 && tx > getWidth() * 2 / 3) {
			keyPressed(-7);
			return;
		}
		touchHoldPos = 0;
		lx = (sx = tx);
		ly = (sy = ty);
		if (!touchCtrlShown)
			return;
		if (ty < 50 && hwa) {
			setSmoothZoom(tx, getWidth());
			touchHoldPos = 8;
		} else if (ty < 50) {
			int b;
			if (tx < getWidth() / 3) {
				b = 1;
			} else if (tx < getWidth() * 2 / 3) {
				b = 2;
			} else {
				b = 3;
			}
			touchHoldPos = b;
		} else if (ty > getHeight() - 50) {
			int b;
			if (tx < getWidth() / 4) {
				b = 4;
			} else if (tx < getWidth() * 2 / 4) {
				b = 5;
			} else if (tx < getWidth() * 3 / 4) {
				b = 6;
			} else {
				b = 7;
			}
			touchHoldPos = b;
		}
		repaint();
	}

	protected final void setSmoothZoom(int dx, int w) {
		dx -= 25;
		w -= 50;
		zoom = 1 + 4f * ((float) dx / w);
		if (zoom < 1.01f)
			zoom = 1;
		if (zoom > 4.99f)
			zoom = 5;
	}

	/**
	 * @return -1 if drag must be inverted, 1 overwise.
	 */
	protected float panDeltaMul() {
		return 1;
	}

	protected final void pointerDragged(int tx, int ty) {
		if (touchHoldPos == 8) {
			setSmoothZoom(tx, getWidth());
			repaint();
			return;
		}
		if (touchHoldPos != 0)
			return;
		x += (tx - lx) * panDeltaMul() / (hwa ? zoom : 1f);
		y += (ty - ly) * panDeltaMul() / (hwa ? zoom : 1f);
		lx = tx;
		ly = ty;
		repaint();
	}

	protected final void pointerReleased(int tx, int ty) {
		if (!touchCtrlShown || touchHoldPos == 0) {
			if (Math.abs(sx - tx) < 10 && Math.abs(sy - ty) < 10) {
				touchCtrlShown = !touchCtrlShown;
			}
		}
		if (touchHoldPos == 8) {
			touchHoldPos = 0;
			repaint();
			return;
		}
		int zone = 0;
		if (ty < 50) {
			int b;
			if (tx < getWidth() / 3) {
				b = 1;
			} else if (tx < getWidth() * 2 / 3) {
				b = 2;
			} else {
				b = 3;
			}
			zone = b;
		} else if (ty > getHeight() - 50) {
			int b;
			if (tx < getWidth() / 4) {
				b = 4;
			} else if (tx < getWidth() * 2 / 4) {
				b = 5;
			} else if (tx < getWidth() * 3 / 4) {
				b = 6;
			} else {
				b = 7;
			}
			zone = b;
		}
		if (zone == touchHoldPos) {
			if (zone >= 1 && zone <= 3 && !resizing) {
				zoom = zone;
				bIApp.midlet.start(bIApp.RUN_ZOOM_VIEW);
			} else if (zone == 7) {
				keyPressed(-7);
			}
		}
		touchHoldPos = 0;
		repaint();
	}
	
	protected final void paintHUD(Graphics g, Font f, boolean drawZoom) {
		int w = getWidth();
		int fh = f.getHeight();
		String zoomN = hwa ? String.valueOf(zoom) : Integer.toString((int) zoom);
		if (zoomN.length() > 3)
			zoomN = zoomN.substring(0, 3);
		zoomN = "x" + zoomN;

		if (drawZoom) {
			g.setColor(0);
			g.fillRect(w - f.stringWidth(zoomN), 0, f.stringWidth(zoomN), fh);
			g.setColor(-1);
			g.drawString(zoomN, w - f.stringWidth(zoomN), 0, 0);
		}
	}

	protected final void drawTouchControls(Graphics g, Font f) {
		int w = getWidth(), h = getHeight();
		int fh = f.getHeight();

		// captions

		fillGrad(g, w * 3 / 4, h - 50, w / 4, 51, 0,
				touchHoldPos == 7 ? 0x357EDE : 0x222222);
		g.setGrayScale(255);
		g.drawString(touchCaps[6], w * (1 + 3 * 2) / 8,
				h - 25 - fh / 2, Graphics.TOP | Graphics.HCENTER);
		g.setGrayScale(255);
		g.drawLine(w * 3 / 4, h - 50, w, h - 50);
		g.drawLine(w * 3 / 4, h - 50, w * 3 / 4, h);

		if (hwa) {
			drawZoomSlider(g, f);
			return;
		}
		for (int i = 0; i < 3; i++) {
			fillGrad(g, w * i / 3, 0, w / 3 + 1, 50, touchHoldPos == (i + 1) ? 0x357EDE : 0x222222,
					0);
			g.setGrayScale(255);
			g.drawString(touchCaps[i], w * (1 + i * 2) / 6, 25 - fh / 2, Graphics.TOP | Graphics.HCENTER);
		}
		// bottom hor line
		g.setGrayScale(255);
		g.drawLine(0, 50, w, 50);
		// vert lines between btns
		g.drawLine(w / 3, 0, w / 3, 50);
		g.drawLine(w * 2 / 3, 0, w * 2 / 3, 50);

	}

	private final void drawZoomSlider(Graphics g, Font f) {
		int px = (int) (25 + ((getWidth() - 50) * (zoom - 1) / 4));

		// slider's body
		if (slider == null) {
			for (int i = 0; i < 10; i++) {
				g.setColor(bIApp.blend(touchHoldPos == 8 ? 0x357EDE : 0x444444, 0xffffff, i * 255 / 9));
				g.drawRoundRect(25 - i, 25 - i, getWidth() - 50 + (i * 2), i * 2, i, i);
			}
		} else {
			int spy = touchHoldPos == 8 ? 20 : 0;
			g.drawRegion(slider, 0, spy, 35, 20, 0, 0, 15, 0);
			g.drawRegion(slider, 35, spy, 35, 20, 0, getWidth() - 35, 15, 0);
			g.setClip(35, 0, getWidth() - 70, 50);
			for (int i = 35; i < getWidth() - 34; i += 20) {
				g.drawRegion(slider, 25, spy, 20, 20, 0, i, 15, 0);
			}
			g.setClip(0, 0, getWidth(), getHeight());
		}

		// slider's pin
		for (int i = 0; i < 15; i++) {
			g.setColor(bIApp.blend(touchHoldPos == 8 ? 0x357EDE : 0x444444, 0, i * 255 / 14));
			g.fillArc(px - 15 + i, 10 + i, 30 - i * 2, 30 - i * 2, 0, 360);
		}
		g.setColor(touchHoldPos == 8 ? 0x357EDE : -1);

		g.drawArc(px - 16, 9, 30, 30, 0, 360);

		String ft = String.valueOf(zoom);
		if (ft.length() > 3) {
			ft = ft.substring(0, 3);
		}
		g.setColor(-1);
		g.drawString(ft, px, 25 - f.getHeight() / 2, Graphics.TOP | Graphics.HCENTER);
	}

	protected final void paintNullImg(Graphics g, Font f) {
		int w = getWidth(), h = getHeight();
		int fh = f.getHeight();
		
		String info;
		if (error) {
			g.setGrayScale(0);
			g.fillRect(0, 0, w, h);
			info = "Failed to load image.";
		} else {
			info = "Preparing";
		}
		g.setGrayScale(0);
		int tw = f.stringWidth(info);
		g.fillRect(w / 2 - tw / 2, h / 2, tw,  fh);
		g.setGrayScale(255);
		g.drawString(info, w / 2, h / 2, Graphics.HCENTER | Graphics.TOP);
		if (hasPointerEvents()) {
			// grads
			fillGrad(g, w * 3 / 4, h - 50, w / 4, 51, 0, 0x222222);
			// lines
			g.setGrayScale(255);
			g.drawLine(w * 3 / 4, h - 50, w, h - 50);
			g.drawLine(w * 3 / 4, h - 50, w * 3 / 4, h);
			// captions
			g.setGrayScale(255);
			g.drawString(touchCaps[6], w * 7 / 8, h - 25 - fh / 2, Graphics.TOP | Graphics.HCENTER);
		}
	}

	/**
	 * Fills an opaque gradient on the canvas.
	 * 
	 * @param g  Graphics object to draw in.
	 * @param x  X.
	 * @param y  Y.
	 * @param w  Width.
	 * @param h  Height.
	 * @param c1 Top color.
	 * @param c2 Bottom color.
	 */
	public static void fillGrad(Graphics g, int x, int y, int w, int h, int c1, int c2) {
		for (int i = 0; i < h; i++) {
			g.setColor(bIApp.blend(c2, c1, i * 255 / h));
			g.drawLine(x, y + i, x + w, y + i);
		}
	}

	/**
	 * Converts qwerty key code to corresponding 12k key code.
	 * 
	 * @param k Original key code.
	 * @return Converted key code.
	 */
	public static int qwertyToNum(int k) {
		char c = (char) k;
		switch (c) {
		case 'r':
		case 'R':
		case 'к':
			return Canvas.KEY_NUM1;

		case 't':
		case 'T':
		case 'е':
			return Canvas.KEY_NUM2;

		case 'y':
		case 'Y':
		case 'н':
			return Canvas.KEY_NUM3;

		case 'f':
		case 'F':
		case 'а':
			return Canvas.KEY_NUM4;

		case 'g':
		case 'G':
		case 'п':
			return Canvas.KEY_NUM5;

		case 'h':
		case 'H':
		case 'р':
			return Canvas.KEY_NUM6;

		case 'v':
		case 'V':
		case 'м':
			return Canvas.KEY_NUM7;

		case 'b':
		case 'B':
		case 'и':
			return Canvas.KEY_NUM8;

		case 'n':
		case 'N':
		case 'т':
			return Canvas.KEY_NUM9;

		case 'm':
		case 'M':
		case 'ь':
			return Canvas.KEY_NUM0;

		default:
			return k;
		}
	}
}
