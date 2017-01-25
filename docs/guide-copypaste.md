---
layout: guide
title: Copy and Paste
---

Content may be pasted or drag-and-dropped from the clipboard onto the [canvas](guide.html#conversation-interface).  

### Limitations

This feature is subject to the limitations defined by the source application, clipboard (supplied by the operating system) 
and the web browser, so the behaviour when pasting content into MeTL will vary depending on the exact combination used.
  
Most applications place a variety of content on the clipboard when something is copied, for example: selected text, 
a file path, an HTML fragment, a reference to an image file, and other data or metadata.
 
The operating system may restrict some of this content to avoid exposing restricted information 
(e.g. system folder structure) in order to enforce security (i.e. sandboxing).
  
The web browser may choose to make only some of the remaining content available to web applications (such as MeTL).

If there is only one accessible item in the clipboard when pasting then MeTL will create a text or image from 
the pasted content.

If more than one accessible item is in the clipboard when pasting then MeTL will prompt the user to 
select how and what to paste:

- Plain text: text will be created from the copied content, without any source formatting.
- Rich text: text and images (where possible) will be created from the copied content, retaining source formatting.
- File: an image will be created from the copied content (if it contains a valid image file).

### Alternatives

Other methods of importing content into MeTL include:
  
- Save an image locally then [Insert](guide-image.html) it 
- [Create](guide-conversation.html) a new conversation by importing a Powerpoint presentation 