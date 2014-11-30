(ns bauble.core
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string]
            [me.raynes.fs :as fs]
            [cheshire.core :refer :all]
            [flambo.conf :as conf]
            [flambo.api :as f :refer [defsparkfn]])
  (import [org.jsoup Jsoup])
  (:gen-class))

(def spark-conf (-> (conf/spark-conf)
                    (conf/master "local")
                    (conf/app-name "bauble")))

(def spark-context (f/spark-context spark-conf))

(def stopwords #{"a" "all" "and" "any" "are" "is" "in" "of" "on" "or" "our" "so"
                 "this" "the" "that" "to" "we"})

(def analysis-directory-path "analysis")

(defn- strip-html-tags [s] (.text (Jsoup/parse s)))

(defn- parse-xml [s]
  (try
    (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))
    (catch Exception e)))

(defsparkfn stackoverflow-post->document [{{id :Id body :Body} :attrs}]
  (when (and (not-empty id) (not-empty body)) [(Integer/parseInt id) body]))

(defn- filtered-document-terms [[_ content] stopwords]
  (->> content
       (strip-html-tags)
       (#(string/split % #"( |\n)"))
       (remove empty?)
       (remove #(stopwords (string/lower-case %)))
       (map #(string/replace % #"\W+" " "))
       (remove (partial = " "))
       (map string/trim)))

;; [id content] -> ([id term-1 term-1-frequency terms-count]
;;                  [id term-2 term-2-frequency terms-count]
;;                  ...)
(defsparkfn document-term-tuples [[id content :as document]]
  (let [terms            (filtered-document-terms document stopwords)
        term-count       (count terms)
        term-frequencies (frequencies terms)]
    (map (fn [term] [id term (term-frequencies term) term-count])
         (distinct terms))))

;; [id term term-frequency term-count] -> [term [id tf]]
(defsparkfn document-term-tf-tuple [[id term term-frequency term-count]]
  [term [id (double (/ term-frequency term-count))]])

(defn- term-idf [documents-count]
  ;; [[term-1 [[document-id term-1 term-1-frequency document-terms-count] ...]]
  ;;  [term-2 [[document-id term-2 term-2-frequency document-terms-count] ...]]
  ;;  ...]
  ;;               |
  ;;               V
  ;; [[term-1 idf] [term-2 idf] ...]
  (f/fn [[term document-term-tuples]]
    (let [total-term-count (count document-term-tuples)]
      [term (Math/log (/ documents-count (+ 1.0 total-term-count)))])))

(defn- filter-document-tfidf-tuples [tuples]
  (->> tuples
       (sort-by last >)
       (map (fn [[a b c]] [a b (read-string (format "%.4f" c))]))
       (take 10)
       (map (partial rest))))

(defn- analysis-result-directory-path [analysis-directory data-file-path]
  (let [file-path-parts (fs/split data-file-path)]
    (string/join "/" (conj (butlast (rest file-path-parts)) analysis-directory))))

(defn- analysis-result-file-path [analysis-type analysis-directory data-file-path]
  (let [file-path-parts (fs/split data-file-path)]
    (str (analysis-result-directory-path analysis-directory data-file-path)
         "/"
         (string/replace (last file-path-parts) #"\W+" "_")
         "_"
         (.substring (str analysis-type) 1)
         "_"
         (System/currentTimeMillis)
         ".json")))

(defn stackoverflow-posts-tfidf-analysis [xml-file-path]
  (let [xml-file (f/text-file spark-context xml-file-path)

        documents (-> xml-file
                      (f/map parse-xml)
                      (f/map stackoverflow-post->document)
                      (f/filter identity)
                      f/cache)

        documents-count (f/count documents)

        ;; [[id term-1 term-1-frequency terms-count] ...]
        documents-term-tuples (-> documents
                                  (f/flat-map document-term-tuples)
                                  f/cache)

        ;; [[term-1 [document-id tf]] [term-2 [document-id tf]] ...]
        documents-term-tf-tuples (-> documents-term-tuples
                                     (f/map document-term-tf-tuple)
                                     f/cache)

        ;; [[term-1 idf] [term-2 idf] ...]
        term-idf-tuples (-> documents-term-tuples
                            (f/group-by (f/fn [[_ term _ _]] term))
                            (f/map (term-idf documents-count))
                            f/cache)

        ;; [[document-id term-1 term-1-tfidf]
        ;;  [document-id term-2 term-2-tfidf]
        ;;  ...]
        document-term-tfidf-tuples (-> (f/join documents-term-tf-tuples term-idf-tuples)
                                       (f/map (f/fn [[term [[id tf] idf]]]
                                                [id term (* tf idf)]))
                                       f/cache)]
    (fs/mkdirs (analysis-result-directory-path analysis-directory-path xml-file-path))
    (-> document-term-tfidf-tuples
        (f/group-by (f/fn [[document-id _ _]] document-id))
        (f/map (f/fn [[document-id tfidf-tuples]]
                 [document-id (filter-document-tfidf-tuples tfidf-tuples)]))
        f/sort-by-key
        f/collect
        ((partial reduce
                  (fn [documents [document-id tuples]]
                    (conj documents {:id document-id :terms tuples}))
                  []))
        (generate-string {:pretty true})
        ((partial spit (analysis-result-file-path :tfidf
                                                  analysis-directory-path
                                                  xml-file-path))))))

(defn -main
  [& args]
  (stackoverflow-posts-tfidf-analysis "data/stackoverflow/gaming/Posts.xml"))
