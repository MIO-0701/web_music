import { createApp } from 'vue'
import App from './App.vue'
import LiveApp from './LiveApp.vue'
import './style.css'
import './live.css'

createApp(new URLSearchParams(window.location.search).has('prototype') ? App : LiveApp).mount('#app')
