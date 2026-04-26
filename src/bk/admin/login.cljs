(ns bk.admin.login
  (:require [reagent.core :as r]
            [bk.supabase :as db]
            [bk.admin.state :as state]))

(defn page []
  (let [email (r/atom "")
        password (r/atom "")
        error (r/atom nil)
        loading? (r/atom false)]
    (fn []
      [:div {:style {:min-height "100vh" :display "flex" :align-items "center"
                     :justify-content "center" :background "var(--cream)"}}
       [:div {:style {:background "#fff" :border-radius "var(--radius)" :padding "48px 40px"
                      :box-shadow "var(--shadow)" :width "100%" :max-width "400px"}}
        [:h1 {:style {:font-size "1.5rem" :font-weight "800" :margin-bottom "8px"
                      :color "var(--dark)"}} "BK Function Hall"]
        [:p {:style {:color "var(--text-light)" :margin-bottom "32px"}} "Admin Panel"]
        [:div.form-group
         [:label.form-label "Email"]
         [:input.form-input {:type "email" :placeholder "admin@example.com"
                             :value @email
                             :on-change #(reset! email (.. % -target -value))}]]
        [:div.form-group
         [:label.form-label "Password"]
         [:input.form-input {:type "password"
                             :value @password
                             :on-change #(reset! password (.. % -target -value))}]]
        (when @error
          [:p {:style {:color "#dc2626" :font-size "14px" :margin-bottom "16px"}} @error])
        [:button {:class "btn-primary"
                  :style {:width "100%"}
                  :disabled @loading?
                  :on-click (fn []
                              (reset! error nil)
                              (reset! loading? true)
                              (db/sign-in @email @password
                                (fn [{:keys [ok] :as result}]
                                  (reset! loading? false)
                                  (if ok
                                    (do
                                      (db/fetch-role #(reset! state/role %))
                                      (reset! state/screen :inquiries))
                                    (reset! error (:error result))))))}
         (if @loading? "Signing in…" "Sign In")]]])))
