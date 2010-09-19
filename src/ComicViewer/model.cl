;;
;; ComicViewer Model
;;

(ns ComicViewer.model
  (:gen-class))

(import '(org.apache.tools.zip ZipFile ZipEntry)
	'(java.text MessageFormat)
	'(javax.swing JFrame ImageIcon)
	'(javax.imageio ImageIO))

(def current-zip-file-name (ref nil))
(def current-zip-file (ref nil))
(def current-page-list (ref nil))
(def end-of-page (ref 0))
(def current-position (ref 0))
(def current-page-image (ref nil))
(def image-updated? (ref false))

(defn get-current-page-image []
  (if @image-updated?
    (dosync
     (ref-set current-page-image
	      (ImageIO/read (.getInputStream @current-zip-file
					     (nth @current-page-list @current-position))))
     (ref-set image-updated? false)))
  @current-page-image)


(defn open-zip-file [filename]
  (let [zip-file (new ZipFile filename)
	page-seq (sort-by (fn [entry] (.getName entry))
			  (filter (fn [n] (not (nil?
						(re-matches #".*[gif|png|jpg]$"
							    (.getName n)))))
				  (enumeration-seq (.getEntries zip-file))))]
    (dosync
     (ref-set current-zip-file-name filename)
     (ref-set current-zip-file zip-file)
     (ref-set current-page-list page-seq)
     (ref-set current-position 0)
     (ref-set end-of-page (- (count page-seq) 1))
     (ref-set current-page-image (get-current-page-image))
     (ref-set image-updated? false))))

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