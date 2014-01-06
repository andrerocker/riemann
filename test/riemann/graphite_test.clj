(ns riemann.graphite-test
  (:use riemann.graphite
        [riemann.time :only [unix-time]]
        clojure.test)
  (:require [riemann.logging :as logging]
            [riemann.client :as client]
            [riemann.index :as index]
            [riemann.core :refer [transition! core stop!]]
            [riemann.transport.tcp :refer [tcp-server]]
            [riemann.transport.graphite :refer [graphite-server]]))

(logging/init)

(deftest graphite-server-test
  (logging/suppress
   ["riemann.transport" "riemann.core" "riemann.pubsub" "riemann.graphite"]
   (let [s1       (graphite-server)
         s2       (tcp-server)
         index    (index/index)
         core     (transition!
                   (core)
                   {:index    index
                    :services [s1 s2]
                    :streams  [index]})
         sendout! (graphite {:path graphite-path-basic})
         client   (client/tcp-client)]
     (try
       (sendout! {:service "service1" :metric 1.0 :time 0})
       (sendout! {:service "service2" :metric 1.0 :time 0})
       (Thread/sleep 100)
       (let [[r1 r2] (client/query client "true")]
         (is (and (#{"service1" "service2"} (:service r1))
                  (= 1.0 (:metric r1))))
         (is (and (#{"service1" "service2"} (:service r2))
                  (= 1.0 (:metric r2)))))
       (finally
         (stop! core))))))

(deftest percentiles
         (is (= (graphite-path-percentiles
                  {:service "foo bar"})
                "foo.bar"))
         (is (= (graphite-path-percentiles
                  {:service "foo bar 1"})
                "foo.bar.1"))
         (is (= (graphite-path-percentiles
                  {:service "foo bar 99"})
                "foo.bar.99"))
         (is (= (graphite-path-percentiles
                  {:service "foo bar 0.99"})
                "foo.bar.99"))
         (is (= (graphite-path-percentiles
                  {:service "foo bar 0.999"})
                "foo.bar.999")))

(deftest ^:graphite ^:integration graphite-test
         (let [g (graphite {:block-start true})]
           (g {:host "riemann.local"
               :service "graphite test"
               :state "ok"
               :description "all clear, uh, situation normal"
               :metric -2
               :time (unix-time)}))

         (let [g (graphite {:block-start true})]
           (g {:service "graphite test"
               :state "ok"
               :description "all clear, uh, situation normal"
               :metric 3.14159
               :time (unix-time)}))

         (let [g (graphite {:block-start true})]
           (g {:host "no-service.riemann.local"
               :state "ok"
               :description "all clear, uh, situation normal"
               :metric 4
               :time (unix-time)})))
