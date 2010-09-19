(defproject ComicViewer "0.1.1-SNAPSHOT"
  :description "클로저 기반의 만화책 뷰어 프로그램"
  :dependencies [[org.clojure/clojure "1.2.0"]
		 [org.clojure/clojure-contrib "1.2.0"]
		 [ant/ant "1.6.5"]]
  :dev-dependencies [[swank-clojure "1.2.1"]]
  :main ComicViewer.core
  :jvm-opts ["-Xdebug"])
