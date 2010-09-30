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

(def image-updated? (atom false))
(defn is-image-update-required? [] @image-updated?)
(defn make-image-update-required [] (reset! image-updated? true))
(defn make-image-update-completed [] (reset! image-updated? false))
(defn change-image-update-status []
  (if @image-updated?
    (reset! image-updated? false)
    (reset! image-updated? true))
  (is-image-update-required?))

(def image-cache (atom nil))
(defn cache-image [model im]
  (do
    (println (format "cache-image : image : %s " im))
    (reset! image-cache im)
    (make-image-update-completed)))
(defn get-cached-image [model] @image-cache)
(defn init-image-cache [model]
  (do
    (println "image-cache initialized...")
    (reset! image-cache nil)
    (make-image-update-completed)))

(def current-position (atom 0))
(defn get-current-page [model]
  @current-position)
(defn go-next-page [model]
  (if (= (get-current-page model) (model :end-of-page))
    nil
    (do
      (reset! current-position (+ (get-current-page model) 1))
      (make-image-update-required))))
(defn go-prev-page [model]
  (if (= (get-current-page model) 0)
    nil
    (do
      (reset! current-position (- (get-current-page model) 1))
      (make-image-update-required))))
(defn go-start-page [model]
  (do
    (reset! current-position 0)
    (make-image-update-required)))
(defn go-end-page [model]
  (do
    (reset! current-position (model :end-of-page))
    (make-image-update-required)))

(defn init-global-environment []
  (do
    (go-start-page nil)
    (init-image-cache nil)))

(def resize-required? (ref true))
(def current-file-directory (ref "."))

(defn dec-resize-factor []
  (do
   (reset! image-resize-factor (max @image-resize-factor-down-threshold
				    (- @image-resize-factor 0.05)))
   (make-image-update-required)))

(defn inc-resize-factor []
  (do
   (reset! image-resize-factor (min @image-resize-factor-up-threshold
				    (+ @image-resize-factor 0.05)))
   (make-image-update-required)))

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
  (println (format "resize-image : %s factor:%f" orig-image @image-resize-factor))
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
      (println (format "resize-image : scaled image : %s" scaled-image))
      scaled-image)))
	  
(defn get-current-page-image [model]
  (println (format "total page:%d current page:%d"
		   (count (model :current-page-list))
		   (get-current-page model)))
  (println (get-cached-image model))
  (println (is-image-update-required?))
  (if (is-image-update-required?)
    (do
      (println "get-current-page-image : image updating...")
      (println "------------------------------------------")
      (println (model :current-zip-file))
      (println (get-current-page model))
      (println (nth (model :current-page-list) 0))
      (println (.getInputStream (model :current-zip-file)
				(nth (model :current-page-list)
				     (get-current-page model))))
      (println (ImageIO/read
		(.getInputStream (model :current-zip-file)
				 (nth (model :current-page-list)
				      (get-current-page model)))))
      (cache-image model (resize-image
			  (ImageIO/read
			   (.getInputStream (model :current-zip-file)
					    (nth (model :current-page-list)
						 (get-current-page model))))))
      (println (get-cached-image model))
      (println "------------------------------------------")))
  (get-cached-image model))

(defn get-current-page-image-1 [model panel]
  (with-image (get-current-page-image model)
    (with-JPanel panel
      ;; (println (format "%d %d %d %d %d %d %d %d"
      ;; 		       @image-clip-x
      ;; 		       @image-clip-y
      ;; 		       image-width
      ;; 		       image-height
      ;; 		       (- (- image-width 1) @image-clip-x)
      ;; 		       (- (- image-height 1) @image-clip-y)
      ;; 		       panel-width
      ;; 		       panel-height))
      (.getSubimage image
		    @image-clip-x @image-clip-y
		    (min panel-width (- (- image-width 1) @image-clip-x))
		    (min panel-height (- (- image-height 1) @image-clip-y))))))

(defn move-clip-area [model page-panel x y]
  (with-image (get-current-page-image model)
    (with-JPanel page-panel
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

(defn close-current-zip-file [model]
  (if (nil? (model :current-zip-file-name))
    nil
    (let [_ (go-start-page model)
	  _ (init-image-cache model)
	  _ (make-image-update-completed)
	  _ (.close (model :current-zip-file))]
      {:current-page-image nil
       :end-of-page 0
       :current-page-list nil
       :current-zip-file nil
       :current-zip-file-name nil})))

(defn open-zip-file [filename]
  (let [zip-file (new ZipFile filename)
	page-seq (sort-by (fn [entry] (.getName entry))
			  (filter (fn [n] (not (nil?
						(re-matches #".*[gif|png|jpg]$"
							    (.getName n)))))
				  (enumeration-seq (.getEntries zip-file))))
	_ (init-global-environment)
	temp-model {:current-zip-file-name filename
		    :current-zip-file zip-file
		    :current-page-list page-seq
		    :end-of-page (- (count page-seq) 1)
		    :current-page-image nil}
	_ (make-image-update-required)]
    (assoc temp-model :current-page-image (get-current-page-image temp-model))))

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
(defn draw-info [g model panel]
  (let [orig-color (.getColor g)
	orig-font (.getFont g)
	panel-width (.getWidth panel)
	panel-height (.getHeight panel)
	image-width (.getWidth (get-current-page-image model))
	image-height (.getHeight (get-current-page-image model))
	image-resize-behavior (if @resize-required? "YES" "NO")
	current-page (get-current-page model)]
    (draw-text g 0 0 Color/WHITE
	       (format "page:%d pagefile:%s panel-width:%d panel-height:%d resize:%s clip-x:%d clip-y:%d resize-factor:%f"
		       current-page
		       (.getName (nth (model :current-page-list) current-page))
		       (int panel-width)
		       (int panel-height)
		       image-resize-behavior
		       (int @image-clip-x)
		       (int @image-clip-y)
		       @image-resize-factor))))

(defn paint-page-panel-1 [g model panel]
  (with-JPanel panel
    (with-image (get-current-page-image-1 model panel)
      (let [resize-factor (get-resize-factor image-width
					     image-height
					     panel-width
					     panel-height)]
	(.drawImage g image 0 0	panel)
	(draw-info g model panel))))
  (draw-text g 40 40 Color/WHITE "TEST!!"))


(defn create-image-panel [model]
  (println "image-panel created")
  (proxy [JPanel ActionListener] []
    (paintComponent [g]
		    (proxy-super paintComponent g)
		    (paint-page-panel-1 g model this)
		    (println (model :current-zip-file-name)))
;;		    (println this))
    (actionPerformed [e]
		     (.repaint this)
		     )))

;;
;; 이 함수는 모델 관련된 것이므로 나중에 위치 바꿀 것
;;
(defn open-file []
  (let [jfc (JFileChooser. @current-file-directory)]
    (.showOpenDialog jfc nil) ;; 나중에 frame-window 와 연계 필요
    (dosync
     (ref-set current-file-directory (.getCurrentDirectory jfc)))
    (println (.getPath (.getSelectedFile jfc)))
    (.getPath (.getSelectedFile jfc))))

(defn destroy-gui [model comps]
  (.stop (:main-timer comps))
  (.dispose (:frame-window comps)))
;;  (close-current-zip-file model))

(defn setup-gui [a])
(defn frame-window-key-listener [model comps]
     (proxy [KeyListener] []
       (keyPressed [e]
		   (let [key-code (.getKeyCode e)]
		     (cond (= key-code KeyEvent/VK_LEFT) (go-prev-page model)
			   (= key-code KeyEvent/VK_RIGHT) (go-next-page model)
			   (= key-code KeyEvent/VK_SLASH) (change-resize-behavior)
			   ;; 새 파일 열기를 하면 아예 새로 창을 열고 현재 창을 닫는다.
			   (= key-code KeyEvent/VK_O) (let [new-model (open-zip-file (open-file))]
							(if (nil? new-model)
							  nil
							  (do
							    (destroy-gui model comps)
							    (setup-gui new-model))))
			   (= key-code KeyEvent/VK_ESCAPE) (destroy-gui model comps)
			   (= key-code KeyEvent/VK_OPEN_BRACKET) (dec-resize-factor)
			   (= key-code KeyEvent/VK_CLOSE_BRACKET) (inc-resize-factor)))
		   (println (format "key %d pressed!" (.getKeyCode e))))
       (keyTyped [e])
       (keyReleased [e])))

(defn frame-window-wheel-listener [model comps]
     (proxy [MouseWheelListener] []
       (mouseWheelMoved [e]
			(println (.paramString e))
			(let [mod (.getModifiers e)
			      mod-ex (.getModifiersEx e)
			      paramString (.paramString e)]
			  (if (and (= mod 0)
				   (= mod-ex 0))
			    (move-clip-area model
					    (:page-panel comps)
					    0 (.getUnitsToScroll e))
			    (if (and (= mod 1)
				     (= mod-ex 64))
			      (move-clip-area model
					      (:page-panel comps)
					      (.getUnitsToScroll e) 0)))))))

(defn setup-actions [model comps]
  (.addActionListener (:main-timer comps) (:page-panel comps))
  (.addKeyListener (:frame-window comps)
		   (frame-window-key-listener model comps))
  (.addMouseWheelListener (:frame-window comps)
			  (frame-window-wheel-listener model comps)))

(defn initialize-gui [model comps]
  (.setSize (:frame-window comps) 500 500)
  (.setSize (:page-panel comps) 500 500)
  (.add (:frame-window comps) (:page-panel comps))
  (setup-actions model comps)
  (.requestFocus (:frame-window comps))
  (.start (:main-timer comps)))

(defn create-gui-components [model]
  (println "new gui components created")
  {:main-timer (Timer. 100 nil)
   :frame-window (JFrame. "ComicViewer")
   :page-panel (create-image-panel model)})

(defn setup-gui [model]
  (let [gui-components (create-gui-components model)]
    (initialize-gui model gui-components)
    (.setVisible (:frame-window gui-components) true)
    (make-image-update-required)))

(defn setup-application []
  (let [model (open-zip-file (open-file))]
    (setup-gui model)))

(defn start-appl []
  (setup-application))

;; (defn -main [& arg]
;;   (start-gui))
