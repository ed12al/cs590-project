(ns gradesheet.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :as ring-resp]
            [gradesheet.layout :as layout]
            [gradesheet.model.user :as user]
            [gradesheet.model.quiz :as quiz]))

(def upper (re-pattern "[A-Z]+"))
(def number (re-pattern "[0-9]+"))
(def special (re-pattern "[\"'!@#$%^&*()?]+"))

(defn strength? [password]
  (and (re-find upper password)
       (re-find number password)
       (re-find special password)))

(defn length? [password]
  (> (count password) 8))

(defn valid-password? [password]
  (and (strength? password) (length? password)))


(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn quiz-page
  [request]
  (try
    (let [inputs (read-string (slurp (:body request)))
          num (Integer. (:quiz inputs))
          jsonQuiz1 (quiz/get-quiz-question-by-id (- (* num 3) 2))
          jsonQuiz2 (quiz/get-quiz-question-by-id (- (* num 3) 1))
          jsonQuiz3 (quiz/get-quiz-question-by-id (* num 3))]
          (bootstrap/json-response [{:num "Q1" :value jsonQuiz1}
                                    {:num "Q2" :value jsonQuiz2}
                                    {:num "Q3" :value jsonQuiz3}]))
    (catch Throwable t
      (ring-resp/response "quiz not found"))))

(defn home-page
  [request]
  (layout/render "home.html"))

(defn register-page
  [request]
  (layout/render "register.html"))

(defn submit-quiz
  [request]
  (try
    (let [inputs (read-string (slurp (:body request)))
          q1 (Integer. (:id (:Q1 inputs)))
          a1 (String. (:answer (:Q1 inputs)))
          q2 (Integer. (:id (:Q2 inputs)))
          a2 (String. (:answer (:Q2 inputs)))
          q3 (Integer. (:id (:Q3 inputs)))
          a3 (String. (:answer (:Q3 inputs)))
          score (atom 0)]
        (if (quiz/correct? q1 a1)
          (swap! score inc))
        (if (quiz/correct? q2 a2)
          (swap! score inc))
        (if (quiz/correct? q3 a3)
          (swap! score inc))
        (ring-resp/response (str @score)))
    (catch Throwable t
      (ring-resp/response "Internal error"))))

(defn check-username
  [request]
  (try
    (let [inputs (read-string (slurp (:body request)))
          username (String. (:username inputs))]
        (if (user/exist-user? username)
          (ring-resp/response "username exists")
          (ring-resp/response "available")))
    (catch Throwable t
      (ring-resp/response "username cannot be empty"))))

(defn check-password
  [request]
  (try
    (let [inputs (read-string (slurp (:body request)))
          password (String. (:password inputs))]
        (if (valid-password? password)
          (ring-resp/response "strong password")
          (ring-resp/response "weak password")))
    (catch Throwable t
      (ring-resp/response "password cannot be empty"))))

(defn submit
  [request]
  (try
    (let [inputs (read-string (slurp (:body request)))
          username (String. (:username inputs))
          password (String. (:password inputs))]
          (if (and (not (user/exist-user? username)) (valid-password? password))
            (do
              (user/add-user {:username username :password password})
              (ring-resp/response "successfully registered"))
            (ring-resp/response "failed to register")))
    (catch Throwable t
      (ring-resp/response "failed to register"))))

(defroutes routes
  ;; Defines "/" and "/about" routes with their associated :get handlers.
  ;; The interceptors defined after the verb map (e.g., {:get home-page}
  ;; apply to / and its children (/about).
  [[["/" {:get home-page}
     ;;^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/about" {:get about-page}]
     ["/register" {:get register-page}]
     ["/check-username" {:post check-username}]
     ["/check-password" {:post check-password}]
     ["/register" {:post submit}]
     ["/quiz" {:post quiz-page}]
     ["/submitQuiz" {:post submit-quiz}]]]])

;; Consumed by gradesheet.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::bootstrap/type :jetty
              ;;::bootstrap/host "localhost"
              ::bootstrap/port 8080})

