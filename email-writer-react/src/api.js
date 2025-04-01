const backendUrl = import.meta.env.VITE_BACKEND_URL;

export const generateReply = async (emailText, tone = "") => {
    try {
        const response = await fetch(`${backendUrl}/api/email/generate`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                emailContent: emailText, // Changed from "email" to "emailContent"
                tone: tone
            }),
        });
        if (!response.ok) {
            throw new Error("Failed to fetch reply");
        }
        return await response.text();
    } catch (error) {
        console.error("Error generating reply:", error);
        throw error;
    }
};
