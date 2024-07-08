/*
Copyright (c) 2024 Arman Jussupgaliyev

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Ticker;
import javax.microedition.midlet.MIDlet;

import cc.nnproject.json.AbstractJSON;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class bIApp extends MIDlet implements Runnable, CommandListener, ItemCommandListener {
	
	private static final int RUN_POSTS = 1;
	private static final int RUN_POST = 2;
	private static final int RUN_THUMBNAILS = 3;
	static final int RUN_ZOOM_VIEW = 5;
	
	private static final int API_DANBOORU = 1;
	private static final int API_GELBOORU = 2;
	private static final int API_SAFEBOORU = 3;
	private static final int API_YANDERE = 4;
	private static final int API_E621 = 5;
	
	private static final String DANBOORU_URL = "https://danbooru.donmai.us/";
	private static final String GELBOORU_URL = "https://gelbooru.com/";
	private static final String SAFEBOORU_URL = "https://safebooru.org/";
	private static final String YANDERE_URL = "https://yande.re/";
	private static final String E621_URL = "https://e621.net/";


	private static final Font largefont = Font.getFont(0, 0, Font.SIZE_LARGE);
	private static final Font medboldfont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
	static final Font smallfont = Font.getFont(0, 0, Font.SIZE_SMALL);
	private static final Font selectedpagefont = Font.getFont(0, Font.STYLE_BOLD | Font.STYLE_ITALIC, Font.SIZE_SMALL);

	public static bIApp midlet;
	private static Display display;
	
	private static Command exitCmd;
	private static Command searchCmd;
	private static Command postsCmd;
	private static Command aboutCmd;
	
	private static Command backCmd;
	private static Command postItemCmd;
	
	private static Command showPostCmd;
	private static Command downloadCmd;

	private static Command prevPageCmd;
	private static Command nextPageCmd;
	private static Command nPageCmd;
	
	private static Form mainForm;
	private static Form postsForm;
	private static Form postForm;
	private static ViewCommon view;
	
	private static TextField searchField;
	
	private static int run;
	private static boolean running;
	
	private static int limit = 10;
	private static int page = 1;
	private static String query;
	
	private static Object thumbLoadLock = new Object();
	private static Vector thumbsToLoad = new Vector();
	private static Hashtable previewUrlsCache = new Hashtable();
	private static Hashtable posts = new Hashtable();
	
	// settings
	private static String proxyUrl = "http://nnp.nnchan.ru/hproxy.php?";
	private static int apiMode = API_DANBOORU;
	static int viewMode = 1;
	static boolean onlineResize = true;
	static boolean keepBitmap;
	
	private static Image postPlaceholderImg = null;
	
	private static ImageItem postItem;
	private static String postId;
	private static JSONObject post;
	
	private static String version;

	public bIApp() {}

	protected void destroyApp(boolean unconditional) {}

	protected void pauseApp() {}

	protected void startApp() {
		if (midlet != null) return;
		midlet = this;
		
		version = getAppProperty("MIDlet-Version");
		display = Display.getDisplay(this);
		
		exitCmd = new Command("Exit", Command.EXIT, 2);
		searchCmd = new Command("Search", Command.ITEM, 1);
		postsCmd = new Command("Posts", Command.ITEM, 1);
		aboutCmd = new Command("About", Command.SCREEN, 4);

		backCmd = new Command("Back", Command.EXIT, 2);
		postItemCmd = new Command("Open", Command.ITEM, 1);
		nextPageCmd = new Command("Next page", Command.SCREEN, 2);
		prevPageCmd = new Command("Prev. page", Command.SCREEN, 3);
		nPageCmd = new Command("Go to page", Command.ITEM, 2);
		
		showPostCmd = new Command("Open", Command.ITEM, 1);
		downloadCmd = new Command("Download", Command.ITEM, 1);
		
		Form f = new Form("ы");
		f.addCommand(aboutCmd);
		f.addCommand(exitCmd);
		f.setCommandListener(this);
		
		String t;
		switch (apiMode) {
		case API_DANBOORU:
			t = "Danbooru";
			break;
		case API_GELBOORU:
			t = "Gelbooru";
			break;
		case API_SAFEBOORU:
			t = "Safebooru";
			break;
		case API_E621:
			t = "e621";
			break;
		case API_YANDERE:
			t = "yande.re";
			break;
		default:
			t = "something";
			break;
		}
		
		StringItem s;
		
		s = new StringItem(null, t);
		s.setFont(Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_LARGE));
		s.setLayout(Item.LAYOUT_CENTER | Item.LAYOUT_NEWLINE_AFTER);
		f.append(s);
		
		searchField = new TextField("Tags", "", 200, TextField.NON_PREDICTIVE);
		f.append(searchField);
		
		s = new StringItem(null, "Search", StringItem.BUTTON);
		s.setFont(Font.getDefaultFont());
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(searchCmd);
		s.setDefaultCommand(searchCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		s = new StringItem(null, "Posts", StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(postsCmd);
		s.setDefaultCommand(postsCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		display.setCurrent(mainForm = f);
		
		// start thumbnails loader thread
		start(RUN_THUMBNAILS);
		
		// start second thread on symbian
		String p = System.getProperty("microedition.platform");
		if (p != null && p.indexOf("platform=S60") != -1) {
			start(RUN_THUMBNAILS);
		}
	}

	public void commandAction(Command c, Displayable d) {
		if (d == postsForm) {
			if (c == backCmd) {
				display(mainForm);
				posts.clear();
				previewUrlsCache.clear();
				postsForm = null;
				return;
			}
		}
		if (d == postForm) {
			if (c == backCmd) {
				display(postsForm);
				try {
					// resume loading
					int l = postsForm.size();
					for (int i = 0; i < l; i++) {
						Item item = postsForm.get(i);
						if (!(item instanceof ImageItem)) continue;
						if (((ImageItem) item).getImage() != null) continue;
						scheduleThumb((ImageItem) item, (String) previewUrlsCache.get(((ImageItem) item).getAltText()));
					}
				} catch (Exception e) {}
				post = null;
				postForm = null;
				return;
			}
		}
		if (c == nextPageCmd || c == prevPageCmd) {
			if (running) return;
			if (c == nextPageCmd) ++page;
			else if (--page < 1) page = 1;
			Form f = new Form("Posts");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			f.setTicker(new Ticker("Loading..."));
			
			display(postsForm = f);
			start(RUN_POSTS);
			return;
		}
		if (c == showPostCmd || c == downloadCmd) {
			try {
				if (post == null) return;
				String url = getFile(post, c == downloadCmd);
				if (url == null)
					return;
				
				if (c != downloadCmd && (url.endsWith("jpg") || url.endsWith("jpeg") || url.endsWith("png") || url.endsWith("webm"))) {
					if (bIApp.viewMode == 1) {
						view = new ViewCommon(false);
					} else if (bIApp.viewMode == 2) {
						view = new ViewHWA();
					} else {
						String vram = System.getProperty("com.nokia.gpu.memory.total");
						if (vram != null && !vram.equals("0")) {
							view = new ViewHWA();
						} else {
							view = new ViewCommon(false);
						}
					}
					display(view);
					return;
				}
				
				if (platformRequest(proxyUrl(url)))
					notifyDestroyed();
			} catch (Throwable e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), postForm);
			}
			return;
		}
		if (c == exitCmd) {
			notifyDestroyed();
			return;
		}
		if (c == backCmd) {
			display(mainForm);
			return;
		}
		if (c == aboutCmd) {
			// о программе
			Form f = new Form("About");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			StringItem s;
			s = new StringItem(null, "unnamed booru j2me reader v" + version);
			s.setFont(largefont);
			s.setLayout(Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
			f.append(s);

			s = new StringItem("Developer", "shinovon");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			s.setFont(Font.getDefaultFont());
			f.append(s);

			s = new StringItem("Idea", "GingerFox87, rmn20");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);

			s = new StringItem("GitHub", "github.com/shinovon");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);

			s = new StringItem("Web", "nnp.nnchan.ru");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);
			
			s = new StringItem(null, "\n292 labs (tm)");
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);
			display(f);
			return;
		}
	}

	public void commandAction(Command c, Item item) {
		if (c == postItemCmd) {
			if (running) return;
			thumbsToLoad.removeAllElements();
			
			Form f = new Form("Post #" + (postId = (postItem = (ImageItem) item).getAltText()));
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			display(postForm = f);
			start(RUN_POST);
			return;
		}
		if (c == searchCmd || c == postsCmd) {
			if (running) return;
			thumbsToLoad.removeAllElements();
			
			Form f = new Form("Posts");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			f.setTicker(new Ticker("Loading..."));
			
			display(postsForm = f);
			query = c == searchCmd ? searchField.getString().trim() : null;
			page = 1;
			start(RUN_POSTS);
			return;
		}
		if (c == nPageCmd) {
			if (running) return;
			int n = Integer.parseInt(((StringItem) item).getText());
			if (n == page) return;
			page = n;
			thumbsToLoad.removeAllElements();
			
			Form f = new Form("Posts");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			f.setTicker(new Ticker("Loading..."));
			
			display(postsForm = f);
			start(RUN_POSTS);
			return;
		}
		commandAction(c, display.getCurrent());
	}

	public void run() {
		int run;
		synchronized(this) {
			run = bIApp.run;
			notify();
		}
		running = run != RUN_THUMBNAILS;
		switch (run) {
		case RUN_POSTS: {
			Form f = postsForm;
			previewUrlsCache.clear();
			posts.clear();
			
			try {
				StringBuffer sb = new StringBuffer(query != null ? "Search" : "Posts");
				if (page > 1) {
					sb.append(" (").append(page).append(')');
					f.addCommand(prevPageCmd);
				}
				f.addCommand(nextPageCmd);
				f.setTitle(sb.toString());
				
				addPageButtons(f);
				
				sb.setLength(0);
				switch (apiMode) {
				case API_SAFEBOORU:
				case API_GELBOORU:
					sb.append("index.php?page=dapi&s=post&q=index&json=1");
					
					if (page > 0)
						sb.append("&pid=").append(page);
					break;
				case API_YANDERE:
					sb.append("post.json?");
					
					if (page > 0)
						sb.append("&page=").append(page);
					break;
				default:
					sb.append("posts.json?");
					
					if (page > 0)
						sb.append("&page=").append(page);
				}
				sb.append("&limit=").append(limit);
				if (query != null) {
					sb.append("&tags=").append(url(query));
				}
				
				AbstractJSON res = api(sb.toString());
				JSONArray posts = res instanceof JSONArray ? (JSONArray) res :
					((JSONObject) res).has("post") ? ((JSONObject) res).getArray("post") :
						((JSONObject) res).getArray("posts");
				
				if (postsForm != f) break;
				
				ImageItem item;
				
				int l = posts.size();
				String url;
				for (int i = 0; i < l; i++) {
					JSONObject p = posts.getObject(i);
					String id = p.getString("id");
					
					item = new ImageItem("",
							postPlaceholderImg,
							Item.LAYOUT_LEFT | Item.LAYOUT_TOP,
							id, Item.BUTTON);
					item.addCommand(postItemCmd);
					item.setDefaultCommand(postItemCmd);
					item.setItemCommandListener(this);
					f.append(item);
					
					bIApp.posts.put(item, p);
					if ((url = getPreviewUrl(p)) != null) {
						scheduleThumb(item, url);
						previewUrlsCache.put(id, url);
					}
				}
				
				addPageButtons(f);
			} catch (NullPointerException e) {
				break;
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), f);
			}
			
			f.setTicker(null);
			break;
		}
		case RUN_POST: {
			String id = postId;
			Image thumb = postItem != null ? postItem.getImage() : null;
			JSONObject post = postItem != null ? (JSONObject) posts.get(postItem) : null;
			postItem = null;
			
			Form f = postForm;
			
			ImageItem item = new ImageItem("", thumb, Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER, id, Item.BUTTON);
			
			item.addCommand(showPostCmd);
			item.addCommand(downloadCmd);
			item.setDefaultCommand(showPostCmd);
			item.setItemCommandListener(this);
			f.append(item);
			
			StringItem s;
			
			try {
				if (post == null) {
					StringBuffer sb = new StringBuffer();
					switch (apiMode) {
					case API_SAFEBOORU:
					case API_GELBOORU:
						sb.append("index.php?page=dapi&s=post&q=index&json=1&id=").append(id);
						break;
					case API_YANDERE:
						throw new Exception("not supported");
					default:
						sb.append("posts/").append(id).append(".json");
					}
					post = (JSONObject) api(sb.toString());
					if (post.has("post")) {
						Object t = post.get("post");
						if (t instanceof JSONArray) {
							post = ((JSONArray) t).getObject(0);
						} else {
							post = (JSONObject) t;
						}
					}
				}
				
				if (postForm != f) break;
				
				bIApp.post = post;
				if (thumb == null) {
					String url;
					if ((url = getPreviewUrl(post)) != null)
						scheduleThumb(item, url);
				}
				
				switch (apiMode) {
				case API_DANBOORU:
					String t;
					if ((t = post.getString("tag_string_artist", null)) != null && t.length() > 0) {
						s = new StringItem("Artist", t);
						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
						s.setFont(smallfont);
						f.append(s);
					}
					
					if ((t = post.getString("tag_string_copyright", null)) != null && t.length() > 0) {
						s = new StringItem("Copyright", t);
						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
						s.setFont(smallfont);
						f.append(s);
					}
					
					if ((t = post.getString("tag_string_character", null)) != null && t.length() > 0) {
						s = new StringItem("Character", t);
						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
						s.setFont(smallfont);
						f.append(s);
					}
					
					if ((t = post.getString("tag_string_general", null)) != null && t.length() > 0) {
						s = new StringItem("General", t);
						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
						s.setFont(smallfont);
						f.append(s);
					}
					
					if ((t = post.getString("tag_string_meta", null)) != null && t.length() > 0) {
						s = new StringItem("Meta", t);
						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
						s.setFont(smallfont);
						f.append(s);
					}
					break;
				case API_E621:
					// TODO
					break;
				case API_GELBOORU:
					s = new StringItem("Tags", post.getString("tags", ""));
					s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
					s.setFont(medboldfont);
					f.append(s);
					break;
				case API_SAFEBOORU:
					s = new StringItem("Tags", post.getString("tags", ""));
					s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
					s.setFont(medboldfont);
					f.append(s);
					break;
				case API_YANDERE:
					s = new StringItem("Tags", post.getString("tags", ""));
					s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER);
					s.setFont(medboldfont);
					f.append(s);
					break;
				default:
					f.append(post.toString());
				}
			} catch (Exception e) {
				e.printStackTrace();
				display(errorAlert(e.toString()), f);
			}
			break;
		}
		case RUN_THUMBNAILS: { // background thumbnails loader thread
			try {
				while (true) {
					synchronized (thumbLoadLock) {
						thumbLoadLock.wait();
					}
					Thread.sleep(200);
					while (thumbsToLoad.size() > 0) {
						int i = 0;
						Object[] o = null;
						
						try {
							synchronized (thumbLoadLock) {
								o = (Object[]) thumbsToLoad.elementAt(i);
								thumbsToLoad.removeElementAt(i);
							}
						} catch (Exception e) {
							continue;
						}
						
						if (o == null) continue;
						
						String url = (String) o[0];
						ImageItem item = (ImageItem) o[1];
						
						try { 
							Image img = getImage(proxyUrl(url));

//							int h = getHeight() / 3;
//							int w = (int) (((float) h / img.getHeight()) * img.getWidth());
//							img = resize(img, w, h);
							
							item.setImage(img);
						} catch (Exception e) {
							e.printStackTrace();
						} 
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		case RUN_ZOOM_VIEW: {
			if (view == null) break;
			view.resize((int) view.zoom);
			break;
		}
		}
		running = false;
	}

	void start(int i) {
		try {
			synchronized(this) {
				run = i;
				new Thread(this).start();
				wait();
			}
		} catch (Exception e) {}
	}

	private String getPreviewUrl(JSONObject p) {
		if (apiMode == API_DANBOORU) {
//			return p.getObject("media_asset").getArray("variants").getObject(0).getString("url");
			return p.getString("preview_file_url");
		}
		if (apiMode == API_GELBOORU || apiMode == API_YANDERE) {
			return p.getString("preview_url");
		}
		if (apiMode == API_E621) {
			return p.getObject("preview").getString("url");
		}
		if (apiMode == API_SAFEBOORU) {
			return SAFEBOORU_URL + "thumbnails/" + p.getString("directory") + "/thumbnail_" + p.getString("image") + '?' + p.getString("id");
		}
		return null;
	}

	private static String getFile(JSONObject p, boolean full) {
		if (apiMode == API_DANBOORU  || apiMode == API_GELBOORU || apiMode == API_YANDERE) {
			if (p.has("large_file_url"))
				return p.getString("large_file_url");
			if (p.has("sample_url") && !full)
				return p.getString("sample_url");
			return p.getString("file_url");
		}
		if (apiMode == API_E621) {
			return p.getObject("file").getString("url");
		}
		return null;
	}

	private void addPageButtons(Form f) {
		// TODO limit
		StringItem s;
		if (page > 1) {
			s = pageButton(-1);
			s.setLayout(Item.LAYOUT_SHRINK | Item.LAYOUT_NEWLINE_BEFORE);
			f.append(s);
			f.append(pageButton(1));
		} else {
			s = pageButton(1);
			s.setLayout(Item.LAYOUT_SHRINK | Item.LAYOUT_NEWLINE_BEFORE);
			f.append(s);
		}
		
		
		if (page > 2) f.append(pageButton(page - 1));
		if (page > 1) f.append(pageButton(page));
		f.append(pageButton(page + 1));
		if (page < 2) f.append(pageButton(page + 2));
		
		s = pageButton(0);
		s.setLayout(Item.LAYOUT_SHRINK | Item.LAYOUT_NEWLINE_AFTER);
		f.append(s);
	}
	
	private StringItem pageButton(int n) {
		StringItem s = new StringItem(null, n == -1 ? "<" : n == 0 ? ">" : Integer.toString(n), StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_SHRINK);
		s.setFont(page == n ? selectedpagefont : smallfont);
		Command c = n == -1 ? prevPageCmd : n == 0 ? nextPageCmd : nPageCmd;
		s.addCommand(c);
		s.setDefaultCommand(c);
		s.setItemCommandListener(this);
		return s;
	}
	
	private static void scheduleThumb(ImageItem img, String url) {
		synchronized (thumbLoadLock) {
			thumbsToLoad.addElement(new Object[] { url, img });
			thumbLoadLock.notifyAll();
		}
	}

	static byte[] getPostImage(String s) throws IOException {
		if (s == null) s = "";
		return get(proxyUrl(getFile(post, false).concat(s)));
	}
	
	static void display(Alert a, Displayable d) {
		if(d == null) {
			display.setCurrent(a);
			return;
		}
		display.setCurrent(a, d);
	}

	static void display(Displayable d) {
		if(d instanceof Alert) {
			display.setCurrent((Alert) d, mainForm);
			return;
		}
		if (d == null)
			d = postForm != null ? postForm : postsForm != null ? postForm : mainForm;
		Displayable p = display.getCurrent();
		display.setCurrent(d);
		if (p instanceof ViewCommon) {
			view = null;
		}
	}

	private static Alert errorAlert(String text) {
		Alert a = new Alert("");
		a.setType(AlertType.ERROR);
		a.setString(text);
		a.setTimeout(2000);
		return a;
	}
	
	// http
	
	private static AbstractJSON api(String url) throws IOException {
		String domain;
		switch (apiMode) {
		case API_DANBOORU:
			domain = DANBOORU_URL;
			break;
		case API_GELBOORU:
			domain = GELBOORU_URL;
			break;
		case API_SAFEBOORU:
			domain = SAFEBOORU_URL;
			break;
		case API_YANDERE:
			domain = YANDERE_URL;
			break;
		case API_E621:
			domain = E621_URL;
			break;
		default:
			throw new RuntimeException("api");
		}
		
		AbstractJSON res;

		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(proxyUrl(domain.concat(url)));
			hc.setRequestMethod("GET");
			int c;
			if ((c = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP ".concat(Integer.toString(c)));
			}
			res = JSON.get(readUtf(in = hc.openInputStream(), (int) hc.getLength()));
		} finally {
			if (in != null) try {
				in.close();
			} catch (IOException e) {}
			if (hc != null) try {
				hc.close();
			} catch (IOException e) {}
		}
//		System.out.println(res);
		return res;
	}

	private static Image getImage(String url) throws IOException {
		byte[] b = get(url);
		return Image.createImage(b, 0, b.length);
	}
	
	private static byte[] readBytes(InputStream inputStream, int initialSize, int bufferSize, int expandSize) throws IOException {
		if (initialSize <= 0) initialSize = bufferSize;
		byte[] buf = new byte[initialSize];
		int count = 0;
		byte[] readBuf = new byte[bufferSize];
		int readLen;
		while ((readLen = inputStream.read(readBuf)) != -1) {
			if(count + readLen > buf.length) {
				byte[] newbuf = new byte[count + expandSize];
				System.arraycopy(buf, 0, newbuf, 0, count);
				buf = newbuf;
			}
			System.arraycopy(readBuf, 0, buf, count, readLen);
			count += readLen;
		}
		if(buf.length == count) {
			return buf;
		}
		byte[] res = new byte[count];
		System.arraycopy(buf, 0, res, 0, count);
		return res;
	}
	
	private static String readUtf(InputStream in, int i) throws IOException {
		byte[] buf = new byte[i <= 0 ? 1024 : i];
		i = 0;
		int j;
		while((j = in.read(buf, i, buf.length - i)) != -1) {
			if ((i += j) >= buf.length) {
				System.arraycopy(buf, 0, buf = new byte[i + 2048], 0, i);
			}
		}
		return new String(buf, 0, i, "UTF-8");
	}
	
	private static byte[] get(String url) throws IOException {
		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(url);
			hc.setRequestMethod("GET");
			int r;
			if((r = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP " + r);
			}
			in = hc.openInputStream();
			return readBytes(in, (int) hc.getLength(), 8*1024, 16*1024);
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException e) {
			}
			try {
				if (hc != null) hc.close();
			} catch (IOException e) {
			}
		}
	}
	
	private static HttpConnection open(String url) throws IOException {
		HttpConnection hc = (HttpConnection) Connector.open(url);
		hc.setRequestProperty("User-Agent", "j2me-client/" + version + " (https://github.com/shinovon)");
		return hc;
	}
	
	private static String proxyUrl(String url) {
		System.out.println(url);
		if(url == null || proxyUrl.length() == 0 || "https://".equals(proxyUrl)) {
			return url;
		}
		return proxyUrl + url(url);
	}
	
	public static String url(String url) {
		StringBuffer sb = new StringBuffer();
		char[] chars = url.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int c = chars[i];
			if (65 <= c && c <= 90) {
				sb.append((char) c);
			} else if (97 <= c && c <= 122) {
				sb.append((char) c);
			} else if (48 <= c && c <= 57) {
				sb.append((char) c);
			} else if (c == 32) {
				sb.append("%20");
			} else if (c == 45 || c == 95 || c == 46 || c == 33 || c == 126 || c == 42 || c == 39 || c == 40
					|| c == 41) {
				sb.append((char) c);
			} else if (c <= 127) {
				sb.append(hex(c));
			} else if (c <= 2047) {
				sb.append(hex(0xC0 | c >> 6));
				sb.append(hex(0x80 | c & 0x3F));
			} else {
				sb.append(hex(0xE0 | c >> 12));
				sb.append(hex(0x80 | c >> 6 & 0x3F));
				sb.append(hex(0x80 | c & 0x3F));
			}
		}
		return sb.toString();
	}

	private static String hex(int i) {
		String s = Integer.toHexString(i);
		return "%".concat(s.length() < 2 ? "0" : "").concat(s);
	}
	
	// image utils

	static Image resize(Image src_i, int size_w, int size_h) {
		// set source size
		int w = src_i.getWidth();
		int h = src_i.getHeight();

		// no change??
		if (size_w == w && size_h == h)
			return src_i;

		int[] dst = new int[size_w * size_h];

		resize_rgb_filtered(src_i, dst, w, h, size_w, size_h);

		// not needed anymore
		src_i = null;

		return Image.createRGBImage(dst, size_w, size_h, true);
	}

	private static final void resize_rgb_filtered(Image src_i, int[] dst, int w0, int h0, int w1, int h1) {
		int[] buffer1 = new int[w0];
		int[] buffer2 = new int[w0];

		// UNOPTIMIZED bilinear filtering:               
		//         
		// The pixel position is defined by y_a and y_b,
		// which are 24.8 fixed point numbers
		// 
		// for bilinear interpolation, we use y_a1 <= y_a <= y_b1
		// and x_a1 <= x_a <= x_b1, with y_d and x_d defining how long
		// from x/y_b1 we are.
		//
		// since we are resizing one line at a time, we will at most 
		// need two lines from the source image (y_a1 and y_b1).
		// this will save us some memory but will make the algorithm 
		// noticeably slower

		for (int index1 = 0, y = 0; y < h1; y++) {

			final int y_a = ((y * h0) << 8) / h1;
			final int y_a1 = y_a >> 8;
			int y_d = y_a & 0xFF;

			int y_b1 = y_a1 + 1;
			if (y_b1 >= h0) {
				y_b1 = h0 - 1;
				y_d = 0;
			}

			// get the two affected lines:
			src_i.getRGB(buffer1, 0, w0, 0, y_a1, w0, 1);
			if (y_d != 0)
				src_i.getRGB(buffer2, 0, w0, 0, y_b1, w0, 1);

			for (int x = 0; x < w1; x++) {
				// get this and the next point
				int x_a = ((x * w0) << 8) / w1;
				int x_a1 = x_a >> 8;
				int x_d = x_a & 0xFF;

				int x_b1 = x_a1 + 1;
				if (x_b1 >= w0) {
					x_b1 = w0 - 1;
					x_d = 0;
				}

				// interpolate in x
				int c12, c34;
				int c1 = buffer1[x_a1];
				int c3 = buffer1[x_b1];

				// interpolate in y:
				if (y_d == 0) {
					c12 = c1;
					c34 = c3;
				} else {
					int c2 = buffer2[x_a1];
					int c4 = buffer2[x_b1];

					final int v1 = y_d & 0xFF;
					final int a_c2_RB = c1 & 0x00FF00FF;
					final int a_c2_AG_org = c1 & 0xFF00FF00;

					final int b_c2_RB = c3 & 0x00FF00FF;
					final int b_c2_AG_org = c3 & 0xFF00FF00;

					c12 = (a_c2_AG_org + ((((c2 >>> 8) & 0x00FF00FF) - (a_c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
							| (a_c2_RB + ((((c2 & 0x00FF00FF) - a_c2_RB) * v1) >> 8)) & 0x00FF00FF;
					c34 = (b_c2_AG_org + ((((c4 >>> 8) & 0x00FF00FF) - (b_c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
							| (b_c2_RB + ((((c4 & 0x00FF00FF) - b_c2_RB) * v1) >> 8)) & 0x00FF00FF;
				}

				// final result

				final int v1 = x_d & 0xFF;
				final int c2_RB = c12 & 0x00FF00FF;

				final int c2_AG_org = c12 & 0xFF00FF00;
				dst[index1++] = (c2_AG_org + ((((c34 >>> 8) & 0x00FF00FF) - (c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
						| (c2_RB + ((((c34 & 0x00FF00FF) - c2_RB) * v1) >> 8)) & 0x00FF00FF;
			}
		}
	}

	/**
	 * Part of tube42 imagelib. Blends 2 colors.
	 * 
	 * @param c1
	 * @param c2
	 * @param value256
	 * @return Blended value.
	 */
	public static final int blend(final int c1, final int c2, final int value256) {

		final int v1 = value256 & 0xFF;
		final int c1_RB = c1 & 0x00FF00FF;
		final int c2_RB = c2 & 0x00FF00FF;

		final int c1_AG = (c1 >>> 8) & 0x00FF00FF;

		final int c2_AG_org = c2 & 0xFF00FF00;
		final int c2_AG = (c2_AG_org) >>> 8;

		// the world-famous tube42 blend with one mult per two components:
		final int rb = (c2_RB + (((c1_RB - c2_RB) * v1) >> 8)) & 0x00FF00FF;
		final int ag = (c2_AG_org + ((c1_AG - c2_AG) * v1)) & 0xFF00FF00;
		return ag | rb;

	}
}
