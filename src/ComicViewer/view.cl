(ns ComicViewer.view
  (:gen-class))

(import '(org.apache.tools.zip ZipFile ZipEntry)
	'(java.text MessageFormat))

(import '(javax.swing Timer JFrame JPanel JLabel JTextField JButton ImageIcon)
	'(java.awt.event ActionListener)
	'(java.awt GridLayout BorderLayout)
	'(javax.imageio ImageIO))

(use 'ComicViewer.model)

(defn paint-page-panel [g panel]
  (if (not (nil? (get-current-page-image)))
    (let [panel-width (.getWidth panel)
	  panel-height (.getHeight panel)
	  image-width (.getWidth (get-current-page-image))
	  image-height (.getHeight (get-current-page-image))
	  size-factor (min (/ panel-width image-width)
			   (/ panel-height image-height))]
      (.drawImage g (get-current-page-image) 0 0
		  (int (* image-width size-factor))
		  (int (* image-height size-factor))
		  panel))
    nil))


(defn image-panel []
  (proxy [JPanel ActionListener] []
    (paintComponent [g]
		    (proxy-super paintComponent g)
		    (paint-page-panel g this))
    (actionPerformed [e]
		     (.repaint this)
		     (println "timer action"))))

(def main-frame (JFrame. "ComicViewer"))
(def page-panel (image-panel))
(def main-timer (Timer. 100 nil))

  
(defn setup-gui []
;;  (.setSize page-panel 500 200)
  (.addActionListener main-timer page-panel)
  (.add main-frame page-panel)
  (.pack main-frame)
  (.start main-timer))

(defn begin-gui []
  (.setVisible main-frame true))

(defn destroy-gui []
  (.stop main-timer)
  (.dispose main-frame))

