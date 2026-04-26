(ns bk.public.faq
  (:require [reagent.core :as r]))

(def faqs
  [{:q "What is the maximum capacity?"
    :a "BK Function Hall can comfortably accommodate up to 500 guests."}
   {:q "Is outside catering allowed?"
    :a "Yes, you are welcome to bring your own caterers. Our fully equipped kitchen is available for their use."}
   {:q "How much advance is required to confirm a booking?"
    :a "We require 30% of the total agreed amount as advance to confirm your booking."}
   {:q "Is parking available?"
    :a "Yes, we have ample parking space for all your guests at no extra charge."}
   {:q "How do I check availability and get a quote?"
    :a "Use the availability calendar above to check your date, then fill in the inquiry form or call us directly. We'll get back to you promptly to discuss your requirements and confirm pricing."}])

(defn faq-item [{:keys [q a]} open? on-toggle]
  [:div {:style {:border-bottom "1px solid var(--cream-dark)" :padding "20px 0"}}
   [:button {:on-click on-toggle
             :style {:width "100%" :text-align "left" :background "none" :border "none"
                     :cursor "pointer" :display "flex" :justify-content "space-between"
                     :align-items "center" :font-size "1rem" :font-weight "600" :color "var(--dark)"}}
    q
    [:span {:style {:font-size "1.4rem" :color "var(--gold)" :line-height "1"}} (if open? "−" "+")]]
   (when open?
     [:p {:style {:margin-top "12px" :color "var(--text-light)" :line-height "1.7"}} a])])

(defn section []
  (let [open-idx (r/atom nil)]
    (fn []
      [:section#faq {:style {:background "#fff"}}
       [:div.container {:style {:max-width "760px"}}
        [:h2.section-title "Frequently Asked Questions"]
        (map-indexed
         (fn [i item]
           ^{:key i}
           [faq-item item
            (= @open-idx i)
            #(reset! open-idx (when (not= @open-idx i) i))])
         faqs)]])))
