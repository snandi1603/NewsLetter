document.querySelectorAll('.feedback-btns button').forEach(btn => {
    btn.addEventListener('click', async function() {
        const articleId = this.dataset.articleId;
        const digestDate = this.dataset.digestDate;
        const feedback = this.dataset.feedback;
        try {
            const response = await fetch(`${API_BASE}/feedback`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ article_id: articleId, digest_date: digestDate, feedback: feedback })
            });
            if (response.ok) {
                const parent = this.parentElement;
                parent.querySelectorAll('button').forEach(b => b.classList.remove('active-like', 'active-dislike'));
                this.classList.add(feedback === 'like' ? 'active-like' : 'active-dislike');
            }
        } catch (e) { console.error('Feedback failed:', e); }
    });
});
