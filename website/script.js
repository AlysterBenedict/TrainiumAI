// =============================================
// Trainium AI — Product Website Scripts
// =============================================

document.addEventListener('DOMContentLoaded', () => {

    // --- Navbar scroll effect ---
    const navbar = document.getElementById('navbar');
    let lastScroll = 0;

    window.addEventListener('scroll', () => {
        const currentScroll = window.pageYOffset;
        if (currentScroll > 60) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
        lastScroll = currentScroll;
    }, { passive: true });

    // --- Mobile nav toggle ---
    const navToggle = document.getElementById('navToggle');
    const navLinks = document.getElementById('navLinks');

    if (navToggle && navLinks) {
        navToggle.addEventListener('click', () => {
            navLinks.classList.toggle('open');
            navToggle.classList.toggle('active');
        });

        // Close mobile nav on link click
        navLinks.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                navLinks.classList.remove('open');
                navToggle.classList.remove('active');
            });
        });
    }

    // --- Smooth scroll for anchor links ---
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const targetId = this.getAttribute('href');
            const target = document.querySelector(targetId);
            if (target) {
                const offset = 80;
                const top = target.getBoundingClientRect().top + window.pageYOffset - offset;
                window.scrollTo({ top, behavior: 'smooth' });
            }
        });
    });

    // --- Intersection Observer: Reveal-on-scroll ---
    const revealElements = document.querySelectorAll(
        '.feature-card, .pipeline-step, .exercise-category, .mode-card, .analytics-card, .capability, .coach-chat-preview, .download-content, .contact-card'
    );

    revealElements.forEach(el => el.classList.add('reveal'));

    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry, index) => {
            if (entry.isIntersecting) {
                setTimeout(() => {
                    entry.target.classList.add('visible');
                }, index * 80);
                observer.unobserve(entry.target);
            }
        });
    }, {
        threshold: 0.1,
        rootMargin: '0px 0px -40px 0px'
    });

    revealElements.forEach(el => observer.observe(el));

    // --- Animate hero progress bar fill ---
    const progressFill = document.querySelector('.sp-fill');
    if (progressFill) {
        setTimeout(() => {
            progressFill.style.width = '42%';
        }, 800);
        // Set initial width to 0 for animation
        progressFill.style.width = '0%';
    }

    // --- Parallax subtle effect on hero glow ---
    const heroGlow = document.querySelector('.hero-glow');
    if (heroGlow) {
        window.addEventListener('mousemove', (e) => {
            const x = (e.clientX / window.innerWidth - 0.5) * 30;
            const y = (e.clientY / window.innerHeight - 0.5) * 30;
            heroGlow.style.transform = `translate(${x}px, ${y}px)`;
        }, { passive: true });
    }

    // --- Exercise tag hover micro-interaction ---
    document.querySelectorAll('.exercise-list span').forEach(tag => {
        tag.addEventListener('mouseenter', function() {
            this.style.transform = 'scale(1.05)';
        });
        tag.addEventListener('mouseleave', function() {
            this.style.transform = 'scale(1)';
        });
    });

    // --- Typing effect for hero badge ---
    const heroBadge = document.querySelector('.hero-badge');
    if (heroBadge) {
        heroBadge.style.opacity = '0';
        heroBadge.style.transform = 'translateY(-10px)';
        setTimeout(() => {
            heroBadge.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
            heroBadge.style.opacity = '1';
            heroBadge.style.transform = 'translateY(0)';
        }, 200);
    }

    // --- Counter animation for hero stats ---
    const statValues = document.querySelectorAll('.stat-value');
    const statObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                animateCounter(entry.target);
                statObserver.unobserve(entry.target);
            }
        });
    }, { threshold: 0.5 });

    statValues.forEach(el => statObserver.observe(el));

    function animateCounter(element) {
        const text = element.textContent;
        const match = text.match(/(\d+)/);
        if (!match) return;

        const target = parseInt(match[1]);
        const suffix = text.replace(match[1], '');
        const duration = 1500;
        const startTime = performance.now();

        function update(currentTime) {
            const elapsed = currentTime - startTime;
            const progress = Math.min(elapsed / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);
            const current = Math.floor(eased * target);

            element.textContent = suffix.startsWith('+')
                ? '+' + current + suffix.replace('+', '').replace(target.toString(), '')
                : current + suffix.replace(target.toString(), '');

            if (progress < 1) {
                requestAnimationFrame(update);
            } else {
                element.textContent = text;
            }
        }

        requestAnimationFrame(update);
    }

    // =============================================
    // --- Interactive Chatbot Controller ---
    // =============================================
    const chatMessagesContainer = document.getElementById('chat-messages');
    const chatInput = document.getElementById('chat-input');
    const chatSendBtn = document.getElementById('chat-send');
    const chatRefreshBtn = document.getElementById('chat-refresh-btn');
    const chatHistoryBtn = document.getElementById('chat-history-btn');
    const chatBackBtn = document.getElementById('chat-back-btn');
    const chatChips = document.getElementById('chat-chips');
    const chatSubChips = document.getElementById('chat-sub-chips');
    const historyDrawer = document.getElementById('chat-history-drawer');
    const drawerCloseBtn = document.getElementById('drawer-close');
    const drawerSessionsList = document.getElementById('drawer-sessions-list');

    let activeSessionId = '';
    let chatHistory = [];
    let savedSessions = [];
    let isTyping = false;
    let currentLanguage = 'English';

    // Local Storage Helpers
    function getSavedSessions() {
        try {
            const data = localStorage.getItem('saved_chats_list');
            return data ? JSON.parse(data) : [];
        } catch (e) {
            console.error('Error loading saved chats', e);
            return [];
        }
    }

    function saveSessions(sessions) {
        try {
            localStorage.setItem('saved_chats_list', JSON.stringify(sessions));
        } catch (e) {
            console.error('Error saving chats', e);
        }
    }

    function saveCurrentSession() {
        const userMsgs = chatHistory.filter(m => m.role === 'user');
        if (userMsgs.length === 0) return; // Don't save empty chats

        const firstQuery = userMsgs[0].content;
        const title = firstQuery.length > 25 ? firstQuery.substring(0, 22) + '...' : firstQuery;
        const timestamp = Date.now();
        const dateStr = new Date(timestamp).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            hour: 'numeric',
            minute: '2-digit',
            hour12: true
        });

        const displayTitle = `"${title}" (${dateStr})`;

        const sessionIndex = savedSessions.findIndex(s => s.id === activeSessionId);
        const sessionData = {
            id: activeSessionId,
            timestamp: timestamp,
            title: displayTitle,
            messages: chatHistory,
            language: currentLanguage
        };

        if (sessionIndex !== -1) {
            savedSessions[sessionIndex] = sessionData;
        } else {
            savedSessions.unshift(sessionData); // Add to top
        }

        saveSessions(savedSessions);
        renderHistoryDrawer();
    }

    // Response Data Bank
    const responses = {
        'English': {
            welcome: "Hello! I'm Trainium AI, your personal fitness intelligence. Multilingual assistant. I have your workout plan and metrics. How can I help you today?",
            motivation: "Remember this: **Consistency beats intensity every single time.** You don't have to be perfect; you just have to show up. Your body is a reflection of your habits, not your excuses. Let's conquer today's target!",
            diet: "Based on your BMI of **24.9** (near upper-normal range) and goal of **weight loss**, here is your hyper-personalized daily macro-balanced diet plan:\n\n* **Breakfast (8:30 AM):** Oats cooked in skimmed milk, topped with 10g almonds and a handful of blueberries. (approx. 320 kcal, 12g protein)\n* **Lunch (1:30 PM):** Grilled chicken breast (150g) or paneer sautéed in olive oil, served with 1 cup of steamed broccoli and half a cup of brown rice. (approx. 420 kcal, 38g protein)\n* **Snacks (5:00 PM):** 1 cup of unsweetened Greek yogurt with chia seeds. (approx. 150 kcal, 15g protein)\n* **Dinner (8:00 PM):** Grilled salmon or tofu (150g) with mixed bell peppers, asparagus, and a fresh green salad. (approx. 350 kcal, 30g protein)\n\n**Total Intake:** ~1240 kcal | **Focus:** High Protein & Low Carb for thermogenic deficit.",
            form: "To avoid injury and maximize efficiency, keep these core form principles in mind:\n\n1. **Core Engagement:** Always brace your abs as if you are about to take a punch. This stabilizes your spine.\n2. **Joint Alignment:** In squats, don't let your knees cave inward or go too far past your toes. Keep joints stacked.\n3. **Breathing:** Exhale on the exertion (concentric phase) and inhale on the release (eccentric phase).\n4. **HUD Cues:** Check your live angle overlay in Trainium AI's Camera mode. Green joint lines mean perfect posture; red indicates corrections needed.",
            cooldown: "Here is a quick **5-minute post-workout cooldown routine**:\n\n1. **Child's Pose (60s):** Relieves tension in the lower back and shoulders.\n2. **Cat-Cow Stretch (60s):** Restores flexibility to the spine.\n3. **Downward-Facing Dog (60s):** Stretches calves, hamstrings, and shoulders.\n4. **Forward Fold (60s):** Relaxes the upper body and hamstrings.\n5. **Deep Breathing (60s):** Bring your heart rate down with slow nasal inhalation.",
            postworkout: "Optimize recovery within **30-45 minutes** post-workout with this ideal meal choice:\n\n* **Option 1:** Whey protein shake (1 scoop) blended with 1 medium banana and water. (approx. 240 kcal, 26g protein, 30g carbs)\n* **Option 2:** Grilled chicken breast (120g) with baked sweet potato (100g). (~300 kcal, 30g protein)\n* **Option 3 (Veg):** Scrambled tofu or boiled egg whites (5-6) with sprouted moong salad. (~220 kcal, 24g protein)\n\n**Goal:** Fast-acting proteins to repair muscle fibers and clean carbs to replenish glycogen stores.",
            refusal: "I am Trainium AI, your on-device fitness intelligence. I can only assist with fitness, nutrition, workout plans, and biometrics queries. Please keep our discussion focused on your health goals!",
            default: "I can absolutely guide you with that! To give you the best fitness advice, make sure you've loaded your biometrics in the Trainium app's AI Body Scan. Let's make sure we are focusing on clean execution, progressive overload, and high protein intake. Do you have a specific question about your squat form, cardio routine, or fat loss diet?",
            switchLang: "Language switched to English. How can I assist you with your fitness and nutrition goals today?"
        },
        'Hindi': {
            welcome: "नमस्ते! मैं Trainium AI हूँ, आपकी व्यक्तिगत फिटनेस इंटेलिजेंस। आज मैं आपकी कैसे मदद कर सकता हूँ?",
            motivation: "याद रखें: **निरंतरता हमेशा तीव्रता को हरा देती है।** आपको हर दिन परफेक्ट होने की ज़रूरत नहीं है, बस आपको कोशिश करते रहना है। आपका शरीर आपकी आदतों का आईना है, बहानों का नहीं। चलिए आज का लक्ष्य हासिल करते हैं!",
            diet: "आपके BMI **24.9** और **वजन घटाने** के लक्ष्य के आधार पर, यहाँ आपका आहार योजना है:\n\n* **नाश्ता (सुबह 8:30):** स्किम्ड मिल्क में पके ओट्स, 10 ग्राम बादाम और ब्लूबेरी के साथ। (~320 kcal)\n* **दोपहर का भोजन (दोपहर 1:30):** ग्रिल्ड चिकन ब्रेस्ट (150 ग्राम) या टोफू, उबली हुई ब्रोकली और आधा कप ब्राउन राइस के साथ। (~420 kcal)\n* **शाम का नाश्ता (शाम 5:00):** चिया सीड्स के साथ बिना चीनी वाली ग्रीक योगर्ट। (~150 kcal)\n* **रात का खाना (रात 8:00):** ग्रिल्ड साल्मन मछली या पनीर, शिमला मिर्च और हरी सलाद के साथ। (~350 kcal)",
            form: "चोट से बचने और अधिक लाभ पाने के लिए, इन बातों का ध्यान रखें:\n\n1. **कोर को सक्रिय रखें:** पेट की मांसपेशियों को कसकर रखें। यह आपकी रीढ़ की हड्डी को सहारा देता है।\n2. **घुटनों की स्थिति:** स्क्वाट्स करते समय घुटनों को अंदर की तरफ न झुकने दें।\n3. **साँस लेना:** वजन उठाते समय साँस छोड़ें और छोड़ते समय साँस लें।",
            cooldown: "यहाँ 5 मिनट का आसान पोस्ट-वर्कआउट कूलडाउन स्ट्रेच रूटीन है:\n\n1. **चाइल्ड पोज़ (60 सेकंड):** पीठ और कंधों के तनाव को कम करता है।\n2. **कैट-काउ स्ट्रेच (60 सेकंड):** रीढ़ की हड्डी के लचीलेपन को बढ़ाता है।\n3. **डाउनवर्ड डॉग (60 सेकंड):** पिंडलियों और कंधों को स्ट्रेच करता है।",
            postworkout: "वर्कआउट के बाद 30-45 मिनट के भीतर इन पौष्टिक भोजन विकल्पों का सेवन करें:\n\n* **विकल्प 1:** 1 स्कूप व्हे प्रोटीन शेक और 1 पका केला। (~240 kcal)\n* **विकल्प 2:** ग्रिल्ड चिकन (120 ग्राम) और उबला हुआ शकरकंद (100 ग्राम)।\n* **विकल्प 3 (शाकाहारी):** मूंग सलाद के साथ सोया पनीर या अंडे की सफेदी।",
            refusal: "मैं केवल फिटनेस, आहार, व्यायाम, और बायोमेट्रिक्स से जुड़े सवालों के जवाब दे सकता हूँ। कृपया फिटनेस से संबंधित प्रश्न ही पूछें!",
            default: "मैं इसमें आपकी ज़रूर मदद करूँगा! फिटनेस से जुड़े किसी भी प्रश्न जैसे कि व्यायाम करने का सही तरीका, उचित आहार योजना, या वज़न घटाने के बारे में आप मुझसे पूछ सकते हैं।",
            switchLang: "भाषा बदलकर हिंदी कर दी गई है। आज मैं आपके फिटनेस और पोषण के लक्ष्यों में आपकी कैसे मदद कर सकता हूँ?"
        },
        'Kannada': {
            welcome: "ನಮಸ್ತೆ! ನಾನು Trainium AI, ನಿಮ್ಮ ವೈಯಕ್ತಿಕ ಫಿಟ್‌ನೆಸ್ ಮಾರ್ಗದರ್ಶಿ. ಇಂದು ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಲಿ?",
            motivation: "ನೆನಪಿಡಿ: **ಸ್ಥಿರತೆಯು ಯಾವಾಗಲೂ ತೀವ್ರತೆಯನ್ನು ಸೋಲಿಸುತ್ತದೆ.** ನೀವು ಪರಿಪೂರ್ಣರಾಗಿರಬೇಕಾಗಿಲ್ಲ; ನೀವು ಪ್ರತಿದಿನ ಪ್ರಯತ್ನಿಸಬೇಕು. ನಿಮ್ಮ ದೇಹವು ನಿಮ್ಮ ಅಭ್ಯಾಸಗಳ ಪ್ರತಿಬಿಂಬವಾಗಿದೆ, ನಿಮ್ಮ ನೆಪಗಳಲ್ಲ. ಇಂದಿನ ಗುರಿಯನ್ನು ಸಾಧಿಸೋಣ!",
            diet: "ನಿಮ್ಮ BMI **24.9** ಮತ್ತು **ತೂಕ ಇಳಿಸುವಿಕೆ** ಗುರಿಯ ಆಧಾರದ ಮೇಲೆ, ನಿಮಗಾಗಿ ಸಿದ್ಧಪಡಿಸಿದ ಆಹಾರ ಯೋಜನೆ ಇಲ್ಲಿದೆ:\n\n* **ಬೆಳಗಿನ ಉಪಾಹಾರ (8:30 AM):** ಕೆನೆರಹಿತ ಹಾಲಿನಲ್ಲಿ ಬೇಯಿಸಿದ ಓಟ್ಸ್, ಬಾದಾಮಿ ಮತ್ತು ಬ್ಲೂಬೆರ್ರಿಗಳೊಂದಿಗೆ. (~320 kcal)\n* **ಮಧ್ಯಾಹ್ನದ ಊಟ (1:30 PM):** ಬೇಯಿಸಿದ ಕೋಳಿ ಮಾಂಸ (150g) ಅಥವಾ ಪನೀರ್, ಬ್ರೊಕೊಲಿ ಮತ್ತು ಸ್ವಲ್ಪ ಬ್ರೌನ್ ರೈಸ್ ಜೊತೆಗೆ. (~420 kcal)\n* **ಸಂಜೆ ಉಪಾಹಾರ (5:00 PM):** ಚಿಯಾ ಬೀಜಗಳೊಂದಿಗೆ ಸಿಹಿಯಿಲ್ಲದ ಗ್ರೀಕ್ ಮೊಸರು. (~150 kcal)\n* **ರಾತ್ರಿ ಊಟ (8:00 PM):** ಬೇಯಿಸಿದ ಮೀನು ಅಥವಾ ತೋಫು, ತರಕಾರಿಗಳು ಮತ್ತು ಹಸಿರು ಸಲಾಡ್‌ನೊಂದಿಗೆ. (~350 kcal)",
            form: "ಗಾಯಗಳನ್ನು ತಡೆಗಟ್ಟಲು ಮತ್ತು ಗರಿಷ್ಠ ಪ್ರಯೋಜನ ಪಡೆಯಲು ಈ ನಿಯಮಗಳನ್ನು ಅನುಸರಿಸಿ:\n\n1. **ಹೊಟ್ಟೆಯ ಸ್ನಾಯುಗಳನ್ನು ಬಿಗಿಗೊಳಿಸಿ:** ಇದು ನಿಮ್ಮ ಬೆನ್ನೆಲುಬಿಗೆ ರಕ್ಷಣೆ ನೀಡುತ್ತದೆ.\n2. **ಮೊಣಕಾಲುಗಳ ಜೋಡಣೆ:** ಸ್ಕ್ವಾಟ್ ಮಾಡುವಾಗ ಮೊಣಕಾಲುಗಳನ್ನು ಒಳಕ್ಕೆ ಬಾಗಿಸಬೇಡಿ.\n3. **ಉಸಿರಾಟ:** ಭಾರ ಎತ್ತುವಾಗ ಉಸಿರು ಹೊರಹಾಕಿ ಮತ್ತು ಬಿಡುವಾಗ ಉಸಿರು ತಗೆದುಕೊಳ್ಳಿ.",
            cooldown: "ಇಲ್ಲಿ 5 ನಿಮಿಷಗಳ ಸುಲಭವಾದ ಕೂಲ್‌ಡೌನ್ ವ್ಯಾಯಾಮಗಳಿವೆ:\n\n1. **ಚೈಲ್ಡ್ ಪೋಸ್ (60 ಸೆ):** ಕೆಳಬೆನ್ನು ಮತ್ತು ಹೆಗಲ ಒತ್ತಡ ಕಡಿಮೆ ಮಾಡುತ್ತದೆ.\n2. **ಕ್ಯಾಟ್-ಕೌ ಸ್ಟ್ರೆಚ್ (60 ಸೆ):** ಬೆನ್ನೆಲುಬಿನ ನಮ್ಯತೆಯನ್ನು ಹೆಚ್ಚಿಸುತ್ತದೆ.\n3. **ಡೌನ್‌ವರ್ಡ್ ಡಾಗ್ (60 ಸೆ):** ಕಾಲುಗಳು ಮತ್ತು ಹೆಗಲ ಸ್ನಾಯುಗಳನ್ನು ಎಳೆಯುತ್ತದೆ.",
            postworkout: "ವ್ಯಾಯಾಮದ ನಂತರ 30-45 ನಿಮಿಷಗಳ ಒಳಗೆ ಈ ಕೆಳಗಿನ ಆಹಾರವನ್ನು ತೆಗೆದುಕೊಳ್ಳಿ:\n\n* **ಆಯ್ಕೆ 1:** 1 ಚಮಚ ಪ್ರೋಟೀನ್ ಶೇಕ್ ಮತ್ತು 1 ಬಾಳೆಹಣ್ಣು. (~240 kcal)\n* **ಆಯ್ಕೆ 2:** ಬೇಯಿಸಿದ ಚಿಕನ್ (120g) ಮತ್ತು ಗೆಣಸು (100g).\n* **ಆಯ್ಕೆ 3 (ಸಸ್ಯಾಹಾರಿ):** ಪನೀರ್ ಬುರ್ಜಿ ಅಥವಾ ಮೊಳಕೆ ಬಂದ ಹೆಸರುಕಾಳು ಸಲಾಡ್.",
            refusal: "ನಾನು ಕೇವಲ ಫಿಟ್‌ನೆಸ್, ಆಹಾರ ಪದ್ಧತಿ, ವ್ಯಾಯಾಮಗಳು ಮತ್ತು ಬಯೋಮೆಟ್ರಿಕ್ಸ್ ಕುರಿತು ಮಾತ್ರ ಉತ್ತರಿಸಬಲ್ಲೆ. ದಯವಿಟ್ಟು ಫಿಟ್‌ನೆಸ್‌ಗೆ ಸಂಬಂಧಿಸಿದ ಪ್ರಶ್ನೆಗಳನ್ನೇ ಕೇಳಿ!",
            default: "ಖಂಡಿತ, ನಾನು ನಿಮಗೆ ಸಹಾಯ ಮಾಡುತ್ತೇನೆ! ಫಿಟ್‌ನೆಸ್, ಸೂಕ್ತ ಆಹಾರ ಕ್ರಮ ಅಥವಾ ವ್ಯಾಯಾಮದ ರೀತಿಗೆ ಸಂಬಂಧಿಸಿದ ಪ್ರಶ್ನೆಗಳನ್ನು ನೀವು ನನ್ನನ್ನು ಕೇಳಬಹುದು.",
            switchLang: "ಭಾಷೆಯನ್ನು ಕನ್ನಡಕ್ಕೆ ಬದಲಾಯಿಸಲಾಗಿದೆ. ಇಂದು ನಿಮ್ಮ ಫಿಟ್‌ನೆಸ್ ಮತ್ತು ಪೌಷ್ಟಿಕಾಂಶದ ಗುರಿಗಳನ್ನು ಸಾಧಿಸಲು ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಲಿ?"
        },
        'Tamil': {
            welcome: "வணக்கம்! நான் Trainium AI, உங்கள் தனிப்பட்ட உடற்பயிற்சி வழிகாட்டி. இன்று நான் உங்களுக்கு எவ்வாறு உதவ வேண்டும்?",
            motivation: "நினைவில் வையுங்கள்: **தொடர்ச்சி எப்போதுமே தீவிரத்தை வெல்லும்.** நீங்கள் சரியானவராக இருக்க வேண்டியதில்லை; நீங்கள் தொடர்ந்து முயற்சிக்க வேண்டும். உங்கள் உடல் உங்கள் பழக்கங்களின் பிரதிபலிப்பு, உங்கள் சாக்குகளின் பிரதிபலிப்பு அல்ல!",
            diet: "உங்கள் பிஎம்ஐ **24.9** மற்றும் **எடை இழப்பு** இலக்கின் அடிப்படையில் உணவுத் திட்டம் இதோ:\n\n* **காலை உணவு (8:30 AM):** ஓட்ஸ் மற்றும் பாதாம் பருப்புடன் கூடிய பால் கஞ்சி. (~320 kcal)\n* **மதிய உணவு (1:30 PM):** வறுத்த கோழி மார்பகம் (150 கிராம்) அல்லது பனீர், அவித்த காலிஃபிளவர் மற்றும் பழுப்பு அரிசி சாதம். (~420 kcal)\n* **மாலை சிற்றுண்டி (5:00 PM):** சியா விதைகளுடன் சர்க்கரையற்ற தயிர். (~150 kcal)\n* **இரவு உணவு (8:00 PM):** சுட்ட மீன் அல்லது டோஃபு மற்றும் பச்சைக் காய்கறி சாலட். (~350 kcal)",
            form: "காயங்களைத் தவிர்க்கவும் உடற்பயிற்சியின் பலனை அதிகரிக்கவும் இந்த விதிகளைப் பின்பற்றுங்கள்:\n\n1. **வயிற்று தசைகளை இறுக்குங்கள்:** இது உங்கள் முதுகெலும்பைப் பாதுகாக்கும்.\n2. **மூட்டு சீரமைப்பு:** ஸ்குவாட்ஸ் செய்யும்போது முழங்கால்களை உட்புறமாக வளைய விடாதீர்கள்.\n3. **சுவாசம்:** எடையைத் தூக்கும்போது மூச்சை வெளியே விடவும், இறக்கும்போது மூச்சை உள்ளிழுக்கவும்.",
            cooldown: "இதோ 5 நிமிட உடற்பயிற்சிக்கு பிந்தைய தளர்வு நீட்சிப் பயிற்சி:\n\n1. **சைல்ட்ஸ் போஸ் (60 வினாடி):** கீழ் முதுகு மற்றும் தோள்பட்டை அழுத்தத்தைக் குறைக்கிறது.\n2. **கேட்-கவ் ஸ்ட்ரெட்ச் (60 வினாடி):** முதுகெலும்பின் நெகிழ்வுத்தன்மையை அதிகரிக்கிறது.\n3. **டவுன்வேர்ட் டாக் (60 வினாடி):** கணுக்கால் மற்றும் தோள்களை நீட்டுகிறது.",
            postworkout: "உடற்பயிற்சி முடிந்த 30-45 நிமிடங்களுக்குள் இந்த உணவுகளை உட்கொள்ளுங்கள்:\n\n* **விருப்பம் 1:** 1 கரண்டி புரோட்டீன் ஷேக் மற்றும் 1 வாழைப்பழம். (~240 kcal)\n* **விருப்பம் 2:** சுட்ட சிக்கன் (120 கிராம்) மற்றும் சர்க்கரைவள்ளிக் கிழங்கு (100 கிராம்).\n* **விருப்பம் 3 (சைவம்):** முளைக்கட்டிய பயறு சாலட் அல்லது சோயா பனீர்.",
            refusal: "நான் உடற்பயிற்சி, உணவு முறை, பயிற்சிகள் மற்றும் உடலளவீடுகள் பற்றிய கேள்விகளுக்கு மட்டுமே பதிலளிக்க முடியும். உடற்பயிற்சி தொடர்பான கேள்விகளை மட்டும் கேட்கவும்!",
            default: "நிச்சயமாக நான் உங்களுக்கு உதவுகிறேன்! உடற்பயிற்சி, டயட் பிளான் அல்லது சரியான முறையில் பயிற்சிகள் செய்வது பற்றி நீங்கள் என்னிடம் கேட்கலாம்.",
            switchLang: "மொழி தமிழுக்கு மாற்றப்பட்டுள்ளது. இன்று உங்கள் உடற்பயிற்சி மற்றும் ஊட்டச்சத்து இலக்குகளை அடைய நான் உங்களுக்கு எவ்வாறு உதவ முடியும்?"
        },
        'Telugu': {
            welcome: "నమస్తే! నేను Trainium AI, మీ పర్సనల్ ఫిట్‌నెస్ గైడ్. ఈ రోజు నేను మీకు ఎలా సహాయపడాలి?",
            motivation: "గుర్తుంచుకోండి: **స్థిరత్వం ఎల్లప్పుడూ తీవ్రతను ఓడిస్తుంది.** మీరు ప్రతిరోజూ పర్ఫెక్ట్‌గా ఉండాల్సిన అవసరం లేదు; మీరు ప్రయత్నిస్తూ ఉంటే చాలు. మీ శరీరం మీ అలవాట్ల ప్రతిబింబం, మీ సాకుల ప్రతిబింబం కాదు!",
            diet: "మీ BMI **24.9** మరియు **బరువు తగ్గడం** లక్ష్యం ఆధారంగా మీ డైట్ ప్లాన్ ఇదిగో:\n\n* **ఉదయం టిఫిన్ (8:30 AM):** బాదంపప్పు మరియు బ్లూబెర్రీలతో వండిన ఓట్స్. (~320 kcal)\n* **మధ్యాహ్న భోజనం (1:30 PM):** గ్రిల్డ్ చికెన్ బ్రెస్ట్ (150g) లేదా పనీర్, ఉడికించిన బ్రోకలీ మరియు కొద్దిగా బ్రౌన్ రైస్. (~420 kcal)\n* **సాయంత్రం స్నాక్స్ (5:00 PM):** చియా విత్తనాలతో కూడిన గ్రీక్ పెరుగు. (~150 kcal)\n* **రాత్రి భోజనం (8:00 PM):** గ్రిల్డ్ సాల్మన్ లేదా తోఫు మరియు ఆకుకూరల సలాడ్. (~350 kcal)",
            form: "గాయాలు కాకుండా ఉండటానికి మరియు వ్యాయామం వల్ల పూర్తి ప్రయోజనం పొందడానికి ఇవి పాటించండి:\n\n1. **పొత్తికడుపు బిగించడం:** ఇది వెన్నెముకకు స్థిరత్వాన్ని ఇస్తుంది.\n2. **మోకాళ్ల అమరిక:** స్క్వాట్స్ చేసేటప్పుడు మోకాళ్లను లోపలికి వంగనివ్వకండి.\n3. **శ్వాస క్రియ:** బరువు ఎత్తేటప్పుడు శ్వాస వదలండి, దించేటప్పుడు శ్వాస తీసుకోండి.",
            cooldown: "ఇదిగో 5 నిమిషాల వ్యాయామం తర్వాతి రిలాక్సేషన్ స్ట్రెచింగ్ రొటీన్:\n\n1. **చైల్డ్ పోస్ (60 సెకన్లు):** నడుము మరియు భుజాల ఒత్తిడిని తగ్గిస్తుంది.\n2. **క్యాట్-కౌ స్ట్రెచ్ (60 సెకన్లు):** వెన్నెముక సాగే గుణాన్ని పెంచుతుంది.\n3. **డౌన్‌వర్డ్ డాగ్ (60 సెకన్లు):** పిక్కలు మరియు భుజాలను సాగదీస్తుంది.",
            postworkout: "వ్యాయామం ముగిసిన 30-45 నిమిషాల్లో ఈ ఆహారాలు తీసుకోండి:\n\n* **ఆప్షన్ 1:** 1 స్కూప్ ప్రోటీన్ షేక్ మరియు 1 అరటిపండు. (~240 kcal)\n* **ఆప్షన్ 2:** గ్రిల్డ్ చికెన్ (120g) మరియు ఉడికించిన చిలగడదుంప (100g).\n* **ఆప్షన్ 3 (వెజ్):** మొలకెత్తిన పెసల సలాడ్ లేదా పనీర్ భుర్జీ.",
            refusal: "నేను కేవలం ఫిట్‌నెస్, డైట్, వ్యายామాలు మరియు బయోమెట్రిక్స్ గురించి మాత్రమే సమాధానాలు ఇవ్వగలను. దయచేసి ఫిట్‌నెస్‌కు సంబంధించిన ప్రశ్నలనే అడగండి!",
            default: "తప్పకుండా, నేను మీకు సహాయం చేస్తాను! వ్యాయామాలు చేసే విధానం, డైట్ ప్లాన్ లేదా బరువు తగ్గడం గురించి మీరు నన్ను అడగవచ్చు.",
            switchLang: "భాష తెలుగులోకి మార్చబడింది. ఈ రోజు మీ ఫిట్‌నెస్ మరియు పోషకాహార లక్ష్యాలను సాధించడంలో నేను మీకు ఎలా సహాయపడగలను?"
        }
    };

    function getBotResponse(userMsg) {
        const msg = userMsg.trim().toLowerCase();
        const langData = responses[currentLanguage] || responses['English'];

        // Check language switch command
        const langMatch = userMsg.match(/assist me in (english|hindi|kannada|tamil|telugu|malayalam)/i);
        if (langMatch) {
            let selectedLang = langMatch[1].charAt(0).toUpperCase() + langMatch[1].slice(1).toLowerCase();
            if (selectedLang === 'Malayalam') {
                currentLanguage = 'English';
                return "ഭാഷ മലയാളത്തിലേക്ക് മാറ്റിയിരിക്കുന്നു. (English mode fallback activated): How can I assist you with your fitness goals today?";
            }
            if (responses[selectedLang]) {
                currentLanguage = selectedLang;
                return responses[selectedLang].switchLang;
            }
        }

        // Refusal checks (non-fitness questions)
        const nonFitnessKeywords = [
            'coding', 'python', 'javascript', 'html', 'css', 'history', 'world war', 'president', 
            'prime minister', 'math', 'algebra', 'chemistry', 'physics', 'politics', 'movie', 'song',
            'write a story', 'tell me a joke'
        ];
        if (nonFitnessKeywords.some(kw => msg.includes(kw))) {
            return langData.refusal;
        }

        // Diet Plan query
        if (msg.includes('diet') || msg.includes('meal') || msg.includes('eat') || msg.includes('protein') || msg.includes('nutrition') || msg.includes('breakfast') || msg.includes('lunch') || msg.includes('dinner')) {
            if (msg.includes('post-workout') || msg.includes('cooldown') || msg.includes('after workout')) {
                return langData.postworkout;
            }
            return langData.diet;
        }

        // Motivation query
        if (msg.includes('motivat') || msg.includes('quote') || msg.includes('inspire') || msg.includes('lazy') || msg.includes('energy') || msg.includes('encourage')) {
            return langData.motivation;
        }

        // Form tips query
        if (msg.includes('form') || msg.includes('tip') || msg.includes('posture') || msg.includes('squat') || msg.includes('correct') || msg.includes('joint') || msg.includes('align') || msg.includes('injury')) {
            return langData.form;
        }

        // Cooldown query
        if (msg.includes('cooldown') || msg.includes('stretch') || msg.includes('recover') || msg.includes('relax')) {
            return langData.cooldown;
        }

        // Default response
        return langData.default;
    }

    // UI Rendering Functions
    function appendMessage(role, content, isThinking = false) {
        if (!chatMessagesContainer) return null;
        const msgDiv = document.createElement('div');
        msgDiv.className = `chat-msg ${role}`;

        if (role === 'bot') {
            const avatarDiv = document.createElement('div');
            avatarDiv.className = 'msg-avatar';
            avatarDiv.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59a14.5 14.5 0 0 1 0-9.18l-7.98-6.19a24.09 24.09 0 0 0 0 21.56l7.98-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>`;
            msgDiv.appendChild(avatarDiv);
        }

        const bubbleDiv = document.createElement('div');
        bubbleDiv.className = 'msg-bubble';
        
        if (isThinking) {
            bubbleDiv.innerHTML = `<div class="typing-indicator"><span></span><span></span><span></span></div>`;
        } else {
            bubbleDiv.innerHTML = formatMarkdown(content);
        }

        msgDiv.appendChild(bubbleDiv);
        chatMessagesContainer.appendChild(msgDiv);
        chatMessagesContainer.scrollTop = chatMessagesContainer.scrollHeight;
        
        return msgDiv;
    }

    // Helper to format simple markdown elements (bold, lists, strong)
    function formatMarkdown(text) {
        if (!text) return '';
        let html = text;
        html = html
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");

        // Restore safe tags
        html = html
            .replace(/&lt;strong&gt;/g, "<strong>")
            .replace(/&lt;\/strong&gt;/g, "</strong>")
            .replace(/&lt;br&gt;/g, "<br>");

        html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        html = html.replace(/^\*\s(.*)$/gm, '• $1');
        html = html.replace(/\n\n/g, '<br><br>');
        html = html.replace(/\n/g, '<br>');
        
        return html;
    }

    // Interactive message sender
    function handleSend() {
        if (!chatInput) return;
        const text = chatInput.value.trim();
        if (!text || isTyping) return;

        chatInput.value = '';
        chatInput.focus();

        chatHistory.push({ role: 'user', content: text });
        appendMessage('user', text);

        isTyping = true;
        if (chatSendBtn) chatSendBtn.disabled = true;

        if (chatSubChips) chatSubChips.classList.add('hidden');

        const thinkingBubble = appendMessage('bot', '', true);
        const rawResponse = getBotResponse(text);

        setTimeout(() => {
            if (!thinkingBubble) return;
            const bubble = thinkingBubble.querySelector('.msg-bubble');
            if (bubble) bubble.innerHTML = '';

            const words = rawResponse.split(' ');
            let wordIndex = 0;
            let currentText = '';

            function typeWord() {
                if (wordIndex < words.length) {
                    currentText += (wordIndex === 0 ? '' : ' ') + words[wordIndex];
                    if (bubble) bubble.innerHTML = formatMarkdown(currentText);
                    if (chatMessagesContainer) chatMessagesContainer.scrollTop = chatMessagesContainer.scrollHeight;
                    wordIndex++;
                    setTimeout(typeWord, 35 + Math.random() * 20);
                } else {
                    chatHistory.push({ role: 'assistant', content: rawResponse });
                    isTyping = false;
                    if (chatSendBtn) chatSendBtn.disabled = false;
                    saveCurrentSession();
                }
            }
            typeWord();
        }, 800 + Math.random() * 400);
    }

    // Refresh chat session (New Chat)
    function initNewChat() {
        if (isTyping) return;
        saveCurrentSession();

        activeSessionId = 'chat_' + Date.now();
        chatHistory = [];
        currentLanguage = 'English';
        if (chatMessagesContainer) chatMessagesContainer.innerHTML = '';
        
        const welcomeText = responses[currentLanguage].welcome;
        chatHistory.push({ role: 'assistant', content: welcomeText });
        appendMessage('bot', welcomeText);
        
        if (chatInput) chatInput.value = '';
        if (chatSubChips) chatSubChips.classList.add('hidden');
    }

    // Load a saved chat session
    function loadChatSession(sessionId) {
        if (isTyping) return;
        const session = savedSessions.find(s => s.id === sessionId);
        if (!session) return;

        activeSessionId = session.id;
        chatHistory = [...session.messages];
        currentLanguage = session.language || 'English';

        if (chatMessagesContainer) chatMessagesContainer.innerHTML = '';
        chatHistory.forEach(msg => {
            appendMessage(msg.role === 'assistant' ? 'bot' : 'user', msg.content);
        });

        if (historyDrawer) historyDrawer.classList.remove('open');
        if (chatSubChips) chatSubChips.classList.add('hidden');
    }

    // Delete a saved session
    function deleteChatSession(sessionId, event) {
        event.stopPropagation();
        if (confirm('Are you sure you want to delete this chat session?')) {
            savedSessions = savedSessions.filter(s => s.id !== sessionId);
            saveSessions(savedSessions);
            renderHistoryDrawer();

            if (activeSessionId === sessionId) {
                initNewChat();
            }
        }
    }

    // Render drawer list
    function renderHistoryDrawer() {
        if (!drawerSessionsList) return;
        drawerSessionsList.innerHTML = '';
        if (savedSessions.length === 0) {
            drawerSessionsList.innerHTML = '<div class="drawer-empty-state">No saved chats yet</div>';
            return;
        }

        savedSessions.forEach(session => {
            const itemDiv = document.createElement('div');
            itemDiv.className = 'drawer-session-item';
            itemDiv.addEventListener('click', () => loadChatSession(session.id));

            const infoDiv = document.createElement('div');
            infoDiv.className = 'session-info';

            const cleanTitle = session.title.replace(/^"|"$/g, '').split(' (')[0];
            const dateStr = new Date(session.timestamp).toLocaleDateString('en-US', {
                month: 'short',
                day: 'numeric',
                hour: 'numeric',
                minute: '2-digit'
            });

            infoDiv.innerHTML = `
                <span class="session-title">${cleanTitle}</span>
                <span class="session-time">${dateStr}</span>
            `;

            const deleteBtn = document.createElement('button');
            deleteBtn.className = 'session-delete-btn';
            deleteBtn.setAttribute('aria-label', 'Delete session');
            deleteBtn.innerHTML = `<i data-lucide="trash-2"></i>`;
            deleteBtn.addEventListener('click', (e) => deleteChatSession(session.id, e));

            itemDiv.appendChild(infoDiv);
            itemDiv.appendChild(deleteBtn);
            drawerSessionsList.appendChild(itemDiv);
        });

        if (window.lucide && typeof window.lucide.createIcons === 'function') {
            window.lucide.createIcons();
        }
    }

    // Setup input actions
    if (chatSendBtn) chatSendBtn.addEventListener('click', handleSend);
    if (chatInput) {
        chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                handleSend();
            }
        });
    }

    if (chatRefreshBtn) chatRefreshBtn.addEventListener('click', initNewChat);

    if (chatHistoryBtn) {
        chatHistoryBtn.addEventListener('click', () => {
            renderHistoryDrawer();
            if (historyDrawer) historyDrawer.classList.toggle('open');
        });
    }

    if (drawerCloseBtn) {
        drawerCloseBtn.addEventListener('click', () => {
            if (historyDrawer) historyDrawer.classList.remove('open');
        });
    }

    if (chatBackBtn) {
        chatBackBtn.addEventListener('click', () => {
            if (historyDrawer && historyDrawer.classList.contains('open')) {
                historyDrawer.classList.remove('open');
            } else {
                const featuresSec = document.getElementById('features');
                if (featuresSec) {
                    featuresSec.scrollIntoView({ behavior: 'smooth' });
                }
            }
        });
    }

    // Handle suggestion chips
    if (chatChips) {
        chatChips.addEventListener('click', (e) => {
            const chip = e.target.closest('.chat-chip');
            if (!chip || isTyping) return;

            if (chip.id === 'language-chip') {
                if (chatSubChips) chatSubChips.classList.toggle('hidden');
            } else {
                const promptName = chip.getAttribute('data-chip');
                let promptText = '';

                if (promptName === 'Diet Plan') {
                    promptText = "Suggest a daily diet plan based on my metrics and goal. For each meal (Breakfast, Lunch, Dinner, Snacks), include details (Quantity, Calories, Time Window, Focus, Dishes) formatted as a list.";
                } else if (promptName === 'Motivation') {
                    promptText = "Give me a motivational quote for my workout.";
                } else if (promptName === 'Form Tips') {
                    promptText = "What are some general tips to improve workout form?";
                }

                if (promptText && chatInput) {
                    chatInput.value = promptText;
                    handleSend();
                }
            }
        });
    }

    // Handle language sub-chips
    if (chatSubChips) {
        chatSubChips.addEventListener('click', (e) => {
            const subChip = e.target.closest('.sub-chip');
            if (!subChip || isTyping) return;

            const lang = subChip.getAttribute('data-lang');
            if (chatInput) {
                chatInput.value = `Assist me in ${lang}`;
                handleSend();
            }
        });
    }

    // Initialization
    savedSessions = getSavedSessions();
    initNewChat();

    // Trigger lucide icon creation
    if (window.lucide && typeof window.lucide.createIcons === 'function') {
        window.lucide.createIcons();
    }
});
