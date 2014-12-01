(defproject bauble "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.jsoup/jsoup "1.6.1"]
                 [me.raynes/fs "1.4.6"]
                 [cheshire "5.3.1"]
                 [yieldbot/flambo "0.4.0-SNAPSHOT"]]
  :git-dependencies  [["https://github.com/mpereira/clojure-opennlp.git"]]
  :source-paths ["src" ".lein-git-deps/clojure-opennlp/src/"]
  :main ^:skip-aot bauble.core
  :target-path "target/%s"
  :plugins  [[cider/cider-nrepl "0.8.1"]
             [lein-git-deps "0.0.2-SNAPSHOT"]]
  :profiles {:provided
             {:dependencies [[org.apache.spark/spark-core_2.10 "1.1.0"]]}
             :dev
             {:aot [bauble.core]}})
