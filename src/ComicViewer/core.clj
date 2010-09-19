(ns ComicViewer.core
  (:gen-class))

(import '(org.apache.tools.zip ZipFile ZipEntry)
	'(java.text MessageFormat))

(import '(javax.swing Timer JFrame JPanel JTextField JButton ImageIcon JFileChooser)
	'(java.awt.event ActionListener MouseWheelListener MouseWheelEvent KeyListener KeyEvent)
	'(java.awt GridLayout BorderLayout Color Font RenderingHints Image)
	'(java.awt.image BufferedImage)
	'(java.awt.geom AffineTransform)
	'(javax.imageio ImageIO)
	'(java.text MessageFormat))

(def image-resize-factor-up-threshold (atom 16))
(def image-resize-factor-down-threshold (atom 0.1))
(def image-resize-factor (atom 1.0))

(def current-zip-file-name (ref nil))
(def current-zip-file (ref nil))
(def current-page-list (ref nil))
(def end-of-page (ref 0))
(def current-position (ref 0))
(def current-page-image (ref nil))

(def image-updated? (ref false))

(def resize-required? (ref true))
(def current-file-directory (ref "."))

(def main-frame (atom nil))
(def main-timer (atom nil))
(def page-panel (atom nil))

(defn dec-resize-factor []
  (dosync
   (reset! image-resize-factor (max @image-resize-factor-down-threshold
				    (- @image-resize-factor 0.05)))
   (ref-set image-updated? true)))

(defn inc-resize-factor []
  (dosync
   (reset! image-resize-factor (min @image-resize-factor-up-threshold
				    (+ @image-resize-factor 0.05)))
   (ref-set image-updated? true)))

(def image-clip-x (atom 0))
(def image-clip-y (atom 0))

(def wheel-speed (atom 2))
(defn get-wheel-speed []
  @wheel-speed)


;; Macros
(defmacro with-JPanel [jpanel & body]
  `(let [~'panel-width (.getWidth ~jpanel)
	 ~'panel-height (.getHeight ~jpanel)]
     (do ~@body)))
     
(defmacro with-image [im & body]
  `(if (nil? ~im)
     nil
     (let [~'image ~im
	   ~'image-width (.getWidth ~im)
	   ~'image-height (.getHeight ~im)]
       (do
	 ~@body))))

;;
;; 이 함수를 메모이제이션하면 어떨까...? 라는 생각을 잠깐 한다.
;; 오히려 이렇게 작은 함수는 매크로로 처리하는것이 더 좋을 것 같다. 계산이 얼마나 된다고...
;;
(defn get-resize-factor [image-width image-height panel-width panel-height]
  (if @resize-required?
    (min (/ panel-width image-width)
	 (/ panel-height image-height))
    1))

(defn change-resize-behavior []
  (dosync
   (let [v (not @resize-required?)]
     (ref-set resize-required? v))))

(defn resize-image [orig-image]
  (with-image orig-image
    (let [scaled-width (int (* image-width @image-resize-factor))
	  scaled-height (int (* image-height @image-resize-factor))
	  scaled-image (BufferedImage. scaled-width scaled-height BufferedImage/TYPE_INT_ARGB)
	  g (.createGraphics scaled-image)
	  affine-xform (AffineTransform/getScaleInstance
			(double @image-resize-factor)
			(double @image-resize-factor))]
      (.setRenderingHint g
			 RenderingHints/KEY_INTERPOLATION
			 RenderingHints/VALUE_INTERPOLATION_BICUBIC)
      (.drawImage g image affine-xform nil)
      (.dispose g)
      scaled-image)))
	  
(defn get-current-page-image []
  (if @image-updated?
    (dosync
     (ref-set current-page-image
	      (resize-image (ImageIO/read (.getInputStream @current-zip-file
							   (nth @current-page-list @current-position)))))
     (ref-set image-updated? false)))
  @current-page-image)


(defn get-current-page-image-1 []
  (with-image (get-current-page-image)
    (with-JPanel @page-panel
      (println (format "%d %d %d %d %d %d %d %d"
		       @image-clip-x
		       @image-clip-y
		       image-width
		       image-height
		       (- (- image-width 1) @image-clip-x)
		       (- (- image-height 1) @image-clip-y)
		       panel-width
		       panel-height))
      (.getSubimage image
		    @image-clip-x @image-clip-y
		    (min panel-width (- (- image-width 1) @image-clip-x))
		    (min panel-height (- (- image-height 1) @image-clip-y))))))


(defn move-clip-area [x y]
  (with-image (get-current-page-image)
    (with-JPanel @page-panel
      (dosync
       (let [new-clip-x (int (+ @image-clip-x (* x (get-wheel-speed))))
	     new-clip-y (int (+ @image-clip-y (* y (get-wheel-speed))))]
	 (reset! image-clip-x (min (max 0 new-clip-x)
				   (max 0 (- image-width panel-width))))
	 (reset! image-clip-y (min (max 0 new-clip-y)
				   (max 0 (- image-height panel-height)))))))))

(defn resize-clip-image [im resize-factor x y w h]
  (if (nil? im)
    nil
    (let [image-width (int (* resize-factor (.getWidth im)))
	  image-height (int (* resize-factor (.getHeight im)))]
      (.getSubImage (.getScaledInstance im image-width image-height)
		    x y w h))))

(defn close-current-zip-file [])

(defn open-zip-file [filename]
  (let [zip-file (new ZipFile filename)
	page-seq (sort-by (fn [entry] (.getName entry))
			  (filter (fn [n] (not (nil?
						(re-matches #".*[gif|png|jpg]$"
							    (.getName n)))))
				  (enumeration-seq (.getEntries zip-file))))]
    (close-current-zip-file)
    (dosync
     (ref-set current-zip-file-name filename)
     (ref-set current-zip-file zip-file)
     (ref-set current-page-list page-seq)
     (ref-set current-position 0)
     (ref-set end-of-page (- (count page-seq) 1))
     (ref-set current-page-image (get-current-page-image))
     (ref-set image-updated? true))))

(defn close-current-zip-file []
  (if (nil? @current-zip-file-name)
    nil
    (dosync
     (ref-set image-updated? false)
     (ref-set current-page-image nil)
     (ref-set end-of-page 0)
     (ref-set current-position 0)
     (ref-set current-page-list nil)
     (.close @current-zip-file)
     (ref-set current-zip-file nil)
     (ref-set current-zip-file-name nil))))

(defn go-next-page []
  (if (= @current-position @end-of-page)
    nil
    (dosync
     (ref-set current-position (+ @current-position 1))
     (ref-set image-updated? true))))

(defn go-prev-page []
  (if (= @current-position 0)
    nil
    (dosync
     (ref-set current-position (- @current-position 1))
     (ref-set image-updated? true))))

(defn setup-model []
  (println "model setup start"))

(defn draw-text [g x y color text]
  (let [orig-color (.getColor g)
	orig-font (.getFont g)
	font (Font. "AppleGothic" Font/PLAIN (int 16))
	frc (.getFontRenderContext g)
	bound-text (.getStringBounds font text frc)]
    (.setColor g Color/BLUE)
    (.setFont g font)
    (.setRenderingHint g
		       RenderingHints/KEY_TEXT_ANTIALIASING
		       RenderingHints/VALUE_TEXT_ANTIALIAS_LCD_HRGB)
    (.fillRect g x y (.getWidth bound-text) (.getHeight bound-text))
    (.setColor g color)
    (.drawString g text
		 (- x (int (.getX bound-text)))
		 (- y (int (.getY bound-text))))
    (.setColor g orig-color)
    (.setFont g orig-font)))


;;
;; 만약 이 함수가 image 가 nil 일때 호출되면
;; 에러를 발생시키게 될 것이다.
;;
(defn draw-info [g panel]
  (let [orig-color (.getColor g)
	orig-font (.getFont g)
	panel-width (.getWidth panel)
	panel-height (.getHeight panel)
	image-width (.getWidth (get-current-page-image))
	image-height (.getHeight (get-current-page-image))
	image-resize-behavior (if @resize-required? "YES" "NO")]
    (draw-text g 0 0 Color/WHITE
	       (format "page:%d pagefile:%s panel-width:%d panel-height:%d resize:%s clip-x:%d clip-y:%d resize-factor:%f"
		       @current-position
		       (.getName (nth @current-page-list @current-position))
		       (int panel-width)
		       (int panel-height)
		       image-resize-behavior
		       (int @image-clip-x)
		       (int @image-clip-y)
		       @image-resize-factor))))

(defn paint-page-panel-1 [g panel]
  (with-JPanel panel
    (with-image (get-current-page-image-1)
      (let [resize-factor (get-resize-factor image-width
					     image-height
					     panel-width
					     panel-height)]
	(.drawImage g image 0 0
;;		    (int (* image-width resize-factor))
;;		    (int (* image-height resize-factor))
		    panel)
	(draw-info g panel)))))

(defn image-panel []
  (proxy [JPanel ActionListener] []
    (paintComponent [g]
		    (proxy-super paintComponent g)
		    (paint-page-panel-1 g this))
    (actionPerformed [e]
		     (.repaint this)
;;		     (println "timer action")
		     )))

(defn setup-gui []
  (reset! main-frame (JFrame. "ComicViewer"))
  (reset! main-timer (Timer. 100 nil))
  (reset! page-panel (image-panel))
  (.addActionListener @main-timer @page-panel)
  (.setSize @main-frame 500 500)
  (.setSize @page-panel 500 500)
  (.add @main-frame @page-panel)
  (.requestFocus @main-frame)
  (.start @main-timer))

(defn begin-gui []
  (.setVisible @main-frame true))

(defn destroy-gui []
  (.stop @main-timer)
  (.dispose @main-frame)
  (close-current-zip-file)
  (reset! page-panel nil)
  (reset! main-timer nil)
  (reset! main-frame nil))

(defn open-file []
  (let [jfc (JFileChooser. @current-file-directory)]
    (.showOpenDialog jfc @main-frame)
    (dosync
     (ref-set current-file-directory (.getCurrentDirectory jfc)))
    (open-zip-file (.getPath (.getSelectedFile jfc)))))

(def main-frame-key-listener
     (proxy [KeyListener] []
       (keyPressed [e]
		   (let [key-code (.getKeyCode e)]
		     (cond (= key-code KeyEvent/VK_LEFT) (go-prev-page)
			   (= key-code KeyEvent/VK_RIGHT) (go-next-page)
			   (= key-code KeyEvent/VK_SLASH) (change-resize-behavior)
			   (= key-code KeyEvent/VK_O) (open-file)
			   (= key-code KeyEvent/VK_ESCAPE) (destroy-gui)
			   (= key-code KeyEvent/VK_OPEN_BRACKET) (inc-resize-factor)
			   (= key-code KeyEvent/VK_CLOSE_BRACKET) (dec-resize-factor)))
		   (println (format "key %d pressed!" (.getKeyCode e))))
       (keyTyped [e])
       (keyReleased [e])))

(def main-frame-wheel-listener
     (proxy [MouseWheelListener] []
       (mouseWheelMoved [e]
			(println (.paramString e))
			(let [mod (.getModifiers e)
			      mod-ex (.getModifiersEx e)
			      paranString (.paramString e)]
			  (if (and (= mod 0)
				   (= mod-ex 0))
			    (move-clip-area 0 (.getUnitsToScroll e))
			    (if (and (= mod 1)
				     (= mod-ex 64))
				     (move-clip-area (.getUnitsToScroll e) 0)))))))

(defn setup-actions []
  (.addKeyListener @main-frame main-frame-key-listener)
  (.addMouseWheelListener @main-frame main-frame-wheel-listener))
  
(defn setup-application []
  (setup-model)
  (setup-gui)
  (setup-actions))

(defn start-appl []
  (setup-application)
  (begin-gui))

;; (defn -main [& arg]
;;   (start-gui))
