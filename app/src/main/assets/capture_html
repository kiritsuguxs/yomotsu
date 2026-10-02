(function() {
    try {
        var html = document.documentElement.outerHTML;
        if (window.HtmlViewer && typeof window.HtmlViewer.showHTML === 'function') {
            window.HtmlViewer.showHTML(html);
        }
        return html;
    } catch (e) {
        return document.body ? document.body.innerHTML : "";
    }
})();
