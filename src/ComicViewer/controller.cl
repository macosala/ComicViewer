(ns ComicViewer.controller
  (:gen-class))

(use 'ComicViewer.view)
(use 'ComicViewer.model)

(import '(org.apache.tools.zip ZipFile ZipEntry)
	'(java.text MessageFormat))

(import '(javax.swing Timer JFrame JPanel JLabel JTextField JButton ImageIcon)
	'(java.awt.event ActionListener KeyListener KeyEvent)
	'(java.awt GridLayout BorderLayout)
	'(javax.imageio ImageIO))

(def label-key-listener
     (proxy [KeyListener] []
       (keyPressed [e]
		   (let [key-code (.getKeyCode e)]
		     (cond (= key-code KeyEvent/VK_LEFT) (go-prev-page)
			   (= key-code KeyEvent/VK_RIGHT) (go-next-page)
			   (= key-code KeyEvent/VK_ESCAPE) (destroy-gui)))
		   (println "key pressed!"))
       (keyTyped [e])
       (keyReleased [e])))

(defn setup-actions []
  (.addKeyListener main-frame label-key-listener))
  

(defn setup-application []
  (setup-model)
  (setup-actions)
  (setup-gui))
;;  (.setIcon image-label (new ImageIcon (get-current-page-image))))

(defn init-temp []
     (open-zip-file "/Users/macosala/Downloads/test.zip"))

(defn start-appl []
  (init-temp)
  (setup-application)
  (begin-gui))
