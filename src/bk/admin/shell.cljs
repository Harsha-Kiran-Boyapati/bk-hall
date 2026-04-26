(ns bk.admin.shell
  (:require [bk.admin.state :as state]
            [bk.admin.login :as login]
            [bk.admin.inquiries :as inquiries]
            [bk.admin.bookings :as bookings]
            [bk.admin.booking-detail :as booking-detail]
            [bk.admin.booking-form :as booking-form]
            [bk.admin.overhead :as overhead]
            [bk.admin.pnl :as pnl]
            [bk.supabase :as db]))

(def nav-items
  [{:screen :inquiries :label "Inquiries"}
   {:screen :bookings  :label "Bookings"}
   {:screen :overhead  :label "Overhead"}
   {:screen :pnl       :label "P&L" :owner-only? true}])

(defn nav []
  [:nav {:style {:background "var(--dark)" :color "#fff" :padding "0 24px"
                 :display "flex" :align-items "center" :gap "4px" :height "56px"}}
   [:span {:style {:font-weight "700" :margin-right "20px" :color "var(--gold-light)"
                   :white-space "nowrap"}}
    "BK Admin"]
   (for [{:keys [screen label owner-only?]} nav-items]
     (when (or (not owner-only?) (= @state/role "owner"))
       ^{:key screen}
       [:button {:on-click #(reset! state/screen screen)
                 :style {:background (if (= @state/screen screen)
                                       "rgba(255,255,255,0.15)" "none")
                         :border "none" :color "#fff" :padding "8px 14px"
                         :border-radius "6px" :cursor "pointer"
                         :font-size "14px" :font-weight "600"}}
        label]))
   [:div {:style {:margin-left "auto"}}
    [:button {:on-click #(db/sign-out
                           (fn [_]
                             (reset! state/session nil)
                             (reset! state/role nil)
                             (reset! state/screen :login)))
              :style {:background "none" :border "1px solid rgba(255,255,255,0.3)"
                      :color "rgba(255,255,255,0.7)" :padding "6px 14px"
                      :border-radius "6px" :cursor "pointer" :font-size "13px"}}
     "Sign Out"]]])

(defn current-screen []
  (case @state/screen
    :inquiries     [inquiries/page]
    :bookings      [bookings/page]
    :booking-detail [booking-detail/page]
    :new-booking   [booking-form/page]
    :overhead      [overhead/page]
    :pnl           (if (= @state/role "owner") [pnl/page] [bookings/page])
    [inquiries/page]))

(defn shell []
  (if (nil? @state/session)
    [login/page]
    [:div {:style {:min-height "100vh" :background "var(--cream)"}}
     [nav]
     [:div {:style {:padding "32px 24px" :max-width "1100px" :margin "0 auto"}}
      [current-screen]]]))
